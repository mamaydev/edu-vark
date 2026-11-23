import { withTransaction } from '../../db/transaction';
import { writeAudit } from '../../lib/audit';
import { AppError, conflict, forbidden, notFound, unprocessable } from '../../lib/errors';
import { formatWib } from '../../lib/time';
import type { VarkType } from '../../types/auth';
import * as topicsRepo from '../topics/topics.repository';
import * as repo from './cases.repository';

export const CASE_NOT_FOUND_MESSAGE = 'studi kasus belum tersedia';

export type SubmissionStatus =
  | 'BELUM_MENGUMPULKAN'
  | 'TIDAK_MENGUMPULKAN'
  | 'MENUNGGU_PENILAIAN'
  | 'SUDAH_DINILAI';

export interface CaseResponse {
  id: string;
  topicId: string;
  theory: string | null;
  instructions: string;
  deadlineAt: string;
  deadlineWib: string;
  status: SubmissionStatus;
  submittedAt: string | null;
  score: number | null;
  comment: string | null;
  answer?: string | null;
  gradedAt?: string | null;
  gradedBy?: string | null;
  submissionCount?: number;
}

function toCaseResponse(row: repo.CaseRow): CaseResponse {
  let status: SubmissionStatus;
  if (row.submission_id) {
    status = row.score === null ? 'MENUNGGU_PENILAIAN' : 'SUDAH_DINILAI';
  } else {
    status = row.before_deadline ? 'BELUM_MENGUMPULKAN' : 'TIDAK_MENGUMPULKAN';
  }

  return {
    id: row.id,
    topicId: row.topic_id,
    theory: row.theory,
    instructions: row.instructions,
    deadlineAt: row.deadline_at.toISOString(),
    deadlineWib: formatWib(row.deadline_at),
    status,
    submittedAt: row.submitted_at?.toISOString() ?? null,
    score: row.score,
    comment: row.comment,
    answer: row.answer_text,
    gradedAt: row.assessed_at?.toISOString() ?? null,
    gradedBy: row.assessed_by_name,
  };
}

function isUniqueViolation(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'code' in error &&
    (error as { code?: string }).code === '23505'
  );
}

export async function getCaseForStudent(
  topicId: string,
  studentId: string,
  vark: VarkType,
): Promise<CaseResponse> {
  if (vark !== 'KINESTHETIC') {
    throw forbidden('VARK_MISMATCH', 'Studi kasus hanya untuk sesi Kinesthetic.');
  }

  return withTransaction(async (client) => {
    const topic = await topicsRepo.findTopicForStudent(client, topicId, studentId, vark);
    if (!topic) throw notFound('TOPIC_NOT_FOUND', 'Topik tidak ditemukan.');

    const row = await repo.findCaseForStudent(client, topicId, studentId);
    if (!row) {
      throw new AppError('CASE_NOT_FOUND', 404, CASE_NOT_FOUND_MESSAGE);
    }
    return toCaseResponse(row);
  });
}

export async function getCaseForLecturer(
  topicId: string,
  lecturerId: string,
): Promise<CaseResponse | null> {
  return withTransaction(async (client) => {
    const topic = await topicsRepo.findTopicForLecturer(client, topicId, lecturerId);
    if (!topic) throw notFound('TOPIC_NOT_FOUND', 'Topik tidak ditemukan.');

    const row = await repo.findCaseForLecturer(client, topicId, lecturerId);
    if (!row) return null;

    const submissionCount = await repo.countSubmissions(client, row.id);
    return { ...toCaseResponse(row), submissionCount };
  });
}

export async function upsertCase(
  topicId: string,
  lecturerId: string,
  input: { theory?: string | null; instructions: string; deadlineAt: Date },
): Promise<CaseResponse> {
  if (Number.isNaN(input.deadlineAt.getTime())) {
    throw unprocessable('DEADLINE_INVALID', 'Deadline tidak valid.');
  }

  return withTransaction(async (client) => {
    const topic = await topicsRepo.findTopicForLecturer(client, topicId, lecturerId);
    if (!topic) throw notFound('TOPIC_NOT_FOUND', 'Topik tidak ditemukan.');

    const existing = await repo.findCaseByTopic(client, topicId);
    let row: repo.CaseRow;

    if (existing) {
      const submissionCount = await repo.countSubmissions(client, existing.id);
      if (
        submissionCount > 0 &&
        existing.deadline_at.getTime() !== input.deadlineAt.getTime()
      ) {
        throw conflict(
          'DEADLINE_LOCKED',
          'Deadline terkunci karena sudah ada submission.',
        );
      }

      row = await repo.updateCase(client, existing.id, {
        theory: input.theory ?? null,
        instructions: input.instructions,
        deadlineAt: input.deadlineAt,
      });
      await writeAudit(client, {
        actorId: lecturerId,
        action: 'CASE_UPDATED',
        entity: 'CASE_STUDY',
        entityId: row.id,
        metadata: { deadlineAt: input.deadlineAt.toISOString() },
      });
    } else {
      row = await repo.insertCase(client, {
        topicId,
        createdBy: lecturerId,
        theory: input.theory ?? null,
        instructions: input.instructions,
        deadlineAt: input.deadlineAt,
      });
      await writeAudit(client, {
        actorId: lecturerId,
        action: 'CASE_CREATED',
        entity: 'CASE_STUDY',
        entityId: row.id,
        metadata: { deadlineAt: input.deadlineAt.toISOString() },
      });
    }

    const submissionCount = await repo.countSubmissions(client, row.id);
    return { ...toCaseResponse(row), submissionCount };
  });
}

export async function submitCase(
  topicId: string,
  studentId: string,
  vark: VarkType,
  answerText: string,
): Promise<{
  submissionId: string;
  submittedAt: string;
  status: SubmissionStatus;
}> {
  if (vark !== 'KINESTHETIC') {
    throw forbidden('VARK_MISMATCH', 'Submission hanya untuk sesi Kinesthetic.');
  }

  const trimmed = answerText.trim();
  const length = [...trimmed].length;
  if (length < 1 || length > 10000) {
    throw unprocessable(
      'ANSWER_LENGTH_INVALID',
      'Jawaban harus 1–10.000 karakter.',
    );
  }

  return withTransaction(async (client) => {
    const locked = await repo.lockCaseForSubmission(client, topicId, studentId);
    if (!locked) {
      throw new AppError('CASE_NOT_FOUND', 404, CASE_NOT_FOUND_MESSAGE);
    }
    if (!locked.allowed) {
      throw conflict('DEADLINE_PASSED', 'Deadline pengumpulan telah lewat.');
    }

    const existing = await repo.findSubmission(client, locked.caseId, studentId);
    if (existing) {
      throw conflict('ALREADY_SUBMITTED', 'Jawaban sudah pernah dikirim.');
    }

    try {
      const submission = await repo.insertSubmission(client, {
        caseId: locked.caseId,
        studentId,
        answerText: trimmed,
      });

      await writeAudit(client, {
        actorId: studentId,
        action: 'SUBMISSION_CREATED',
        entity: 'SUBMISSION',
        entityId: submission.id,
        metadata: { caseStudyId: locked.caseId, length },
      });

      return {
        submissionId: submission.id,
        submittedAt: submission.submitted_at.toISOString(),
        status: 'MENUNGGU_PENILAIAN' as const,
      };
    } catch (error) {
      if (isUniqueViolation(error)) {
        throw conflict('ALREADY_SUBMITTED', 'Jawaban sudah pernah dikirim.');
      }
      throw error;
    }
  });
}
