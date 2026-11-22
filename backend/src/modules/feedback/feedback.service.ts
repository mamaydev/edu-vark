import { withTransaction } from '../../db/transaction';
import { writeAudit } from '../../lib/audit';
import { AppError, forbidden, notFound } from '../../lib/errors';
import { MATERIAL_NOT_AVAILABLE_MESSAGE } from '../../lib/messages';
import type { VarkType } from '../../types/auth';
import * as topicsRepo from '../topics/topics.repository';
import * as repo from './feedback.repository';

export interface FeedbackResponse {
  value: number | null;
  updatedAt: string | null;
}

function assertFeedbackVark(vark: VarkType): void {
  if (vark === 'KINESTHETIC') {
    throw forbidden(
      'FEEDBACK_NOT_APPLICABLE',
      'Feedback hanya tersedia untuk Visual, Audio, dan Read/Write.',
    );
  }
}

async function requireAvailableTopic(
  client: Parameters<typeof topicsRepo.findTopicForStudent>[0],
  topicId: string,
  studentId: string,
  vark: VarkType,
): Promise<void> {
  const topic = await topicsRepo.findTopicForStudent(client, topicId, studentId, vark);
  if (!topic) {
    throw notFound('TOPIC_NOT_FOUND', 'Topik tidak ditemukan.');
  }
  if (!topic.material_available) {
    throw new AppError(
      'MATERIAL_NOT_AVAILABLE',
      404,
      MATERIAL_NOT_AVAILABLE_MESSAGE,
    );
  }
}

export async function getFeedback(
  studentId: string,
  topicId: string,
  vark: VarkType,
): Promise<FeedbackResponse> {
  assertFeedbackVark(vark);
  return withTransaction(async (client) => {
    await requireAvailableTopic(client, topicId, studentId, vark);
    const row = await repo.findFeedback(client, studentId, topicId, vark);
    return {
      value: row?.value ?? null,
      updatedAt: row?.updated_at.toISOString() ?? null,
    };
  });
}

export async function saveFeedback(
  studentId: string,
  topicId: string,
  vark: VarkType,
  value: number,
): Promise<FeedbackResponse> {
  assertFeedbackVark(vark);
  return withTransaction(async (client) => {
    await requireAvailableTopic(client, topicId, studentId, vark);
    const row = await repo.upsertFeedback(client, {
      studentId,
      topicId,
      vark,
      value,
    });
    await writeAudit(client, {
      actorId: studentId,
      action: 'FEEDBACK_SAVED',
      entity: 'FEEDBACK',
      entityId: topicId,
      metadata: { vark, value },
    });
    return {
      value: row.value,
      updatedAt: row.updated_at.toISOString(),
    };
  });
}
