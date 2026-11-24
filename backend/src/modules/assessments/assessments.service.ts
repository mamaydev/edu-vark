import { withTransaction } from '../../db/transaction';
import { writeAudit } from '../../lib/audit';
import { conflict, forbidden, notFound, unprocessable } from '../../lib/errors';
import type { UserRole } from '../../types/auth';
import { CASE_NOT_FOUND_MESSAGE } from '../cases/cases.service';
import * as repo from './assessments.repository';

export type SubmissionStatus =
  | 'BELUM_MENGUMPULKAN'
  | 'TIDAK_MENGUMPULKAN'
  | 'MENUNGGU_PENILAIAN'
  | 'SUDAH_DINILAI';

export interface CaseSubmissionSummary {
  studentId: string;
  fullName: string;
  loginId: string;
  submissionId: string | null;
  submittedAt: string | null;
  status: SubmissionStatus;
  score: number | null;
}

export interface SubmissionDetail {
  id: string;
  caseStudyId: string;
  topicId: string;
  studentId: string;
  studentName: string;
  answerText: string;
  submittedAt: string;
  status: SubmissionStatus;
  score: number | null;
  comment: string | null;
  assessedAt: string | null;
}

export interface AssessmentResult {
  id: string;
  submissionId: string;
  score: number;
  comment: string | null;
  assessedAt: string;
}

function statusFor(
  submissionId: string | null,
  score: number | null,
  beforeDeadline: boolean,
): SubmissionStatus {
  if (submissionId) {
    return score === null ? 'MENUNGGU_PENILAIAN' : 'SUDAH_DINILAI';
  }
  return beforeDeadline ? 'BELUM_MENGUMPULKAN' : 'TIDAK_MENGUMPULKAN';
}

function isUniqueViolation(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'code' in error &&
    (error as { code?: string }).code === '23505'
  );
}

export async function listCaseSubmissions(
  caseId: string,
  lecturerId: string,
): Promise<CaseSubmissionSummary[]> {
  return withTransaction(async (client) => {
    const caseRow = await repo.findCaseById(client, caseId);
    if (!caseRow) {
      throw notFound('CASE_NOT_FOUND', CASE_NOT_FOUND_MESSAGE);
    }

    const allowed = await repo.isLecturerForClass(
      client,
      caseRow.class_id,
      lecturerId,
    );
    if (!allowed) {
      throw forbidden('CLASS_FORBIDDEN', 'Anda bukan dosen kelas ini.');
    }

    const rows = await repo.listCaseStudents(client, caseId);
    return rows.map((row) => ({
      studentId: row.student_id,
      fullName: row.full_name,
      loginId: row.login_id,
      submissionId: row.submission_id,
      submittedAt: row.submitted_at?.toISOString() ?? null,
      status: statusFor(row.submission_id, row.score, row.before_deadline),
      score: row.score,
    }));
  });
}

export async function getSubmissionDetail(
  submissionId: string,
  userId: string,
  role: UserRole,
): Promise<SubmissionDetail> {
  return withTransaction(async (client) => {
    const row = await repo.findSubmissionDetail(client, submissionId);
    if (!row) {
      throw notFound('SUBMISSION_NOT_FOUND', 'Submission tidak ditemukan.');
    }

    if (role === 'STUDENT') {
      if (row.student_id !== userId) {
        throw forbidden('SUBMISSION_FORBIDDEN', 'Submission ini bukan milik Anda.');
      }
      // DEC-009/FR-028: enrollment LEFT mencabut akses mahasiswa ke jawaban dan
      // nilainya. Data tetap tersimpan dan dapat dibaca dosen berwenang, serta
      // pulih setelah enrollment diaktifkan kembali.
      if (row.enrollment_status !== 'ACTIVE') {
        throw forbidden(
          'ENROLLMENT_INACTIVE',
          'Akses jawaban dan nilai memerlukan enrollment aktif pada kelas ini.',
        );
      }
    } else if (role === 'LECTURER') {
      const allowed = await repo.isLecturerForClass(
        client,
        row.class_id,
        userId,
      );
      if (!allowed) {
        throw forbidden('CLASS_FORBIDDEN', 'Anda bukan dosen kelas ini.');
      }
    } else {
      throw forbidden('ADMIN_ACADEMIC_FORBIDDEN', 'Admin tidak dapat mengakses submission.');
    }

    return {
      id: row.id,
      caseStudyId: row.case_study_id,
      topicId: row.topic_id,
      studentId: row.student_id,
      studentName: row.student_name,
      answerText: row.answer_text,
      submittedAt: row.submitted_at.toISOString(),
      status: row.assessment_id ? 'SUDAH_DINILAI' : 'MENUNGGU_PENILAIAN',
      score: row.score,
      comment: row.comment,
      assessedAt: row.assessed_at?.toISOString() ?? null,
    };
  });
}

export async function createAssessment(
  submissionId: string,
  lecturerId: string,
  score: number,
  comment: string | null,
): Promise<AssessmentResult> {
  if (!Number.isInteger(score) || score < 0 || score > 100) {
    throw unprocessable('INVALID_SCORE', 'Nilai harus berupa integer 0–100.');
  }

  const normalizedComment = comment?.trim() || null;
  if (normalizedComment && [...normalizedComment].length > 1000) {
    throw unprocessable('INVALID_COMMENT', 'Komentar maksimal 1.000 karakter.');
  }

  return withTransaction(async (client) => {
    const row = await repo.findSubmissionDetail(client, submissionId);
    if (!row) {
      throw notFound('SUBMISSION_NOT_FOUND', 'Submission tidak ditemukan.');
    }

    const allowed = await repo.isLecturerForClass(client, row.class_id, lecturerId);
    if (!allowed) {
      throw forbidden('CLASS_FORBIDDEN', 'Anda bukan dosen kelas ini.');
    }

    if (row.assessment_id) {
      throw conflict('ALREADY_ASSESSED', 'Submission sudah dinilai dan bersifat final.');
    }

    try {
      const assessment = await repo.insertAssessment(client, {
        submissionId,
        lecturerId,
        score,
        comment: normalizedComment,
      });

      await writeAudit(client, {
        actorId: lecturerId,
        action: 'ASSESSMENT_CREATED',
        entity: 'ASSESSMENT',
        entityId: assessment.id,
        metadata: { submissionId, score },
      });

      return {
        id: assessment.id,
        submissionId,
        score,
        comment: normalizedComment,
        assessedAt: assessment.assessed_at.toISOString(),
      };
    } catch (error) {
      if (isUniqueViolation(error)) {
        throw conflict('ALREADY_ASSESSED', 'Submission sudah dinilai dan bersifat final.');
      }
      throw error;
    }
  });
}
