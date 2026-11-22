import { withTransaction } from '../../db/transaction';
import { forbidden, notFound } from '../../lib/errors';
import * as repo from './analytics.repository';

export type FeedbackCategory = 'VISUAL' | 'AURAL' | 'READ_WRITE';

export interface HistogramBucket {
  bucket: string;
  count: number;
}

export interface StudentFeedback {
  studentId: string;
  fullName: string;
  loginId: string;
  vark: FeedbackCategory;
  feedback: Record<FeedbackCategory, number | null>;
}

export interface TopicAnalytics {
  topicId: string;
  category: FeedbackCategory | null;
  studentId: string | null;
  respondentCount: number;
  mean: number | null;
  histogram: HistogramBucket[];
  notRespondedCount: number;
  aggregation: string;
  kinesthetic: {
    assessedCount: number;
    meanScore: number | null;
    notSubmittedCount: number;
  };
  students: StudentFeedback[];
}

const CATEGORIES: FeedbackCategory[] = ['VISUAL', 'AURAL', 'READ_WRITE'];
const BUCKETS = ['0-20', '21-40', '41-60', '61-80', '81-100'];

function bucketIndex(value: number): number {
  if (value <= 20) return 0;
  if (value <= 40) return 1;
  if (value <= 60) return 2;
  if (value <= 80) return 3;
  return 4;
}

function roundedMean(values: number[]): number | null {
  if (values.length === 0) return null;
  const total = values.reduce((sum, value) => sum + value, 0);
  return Math.round((total / values.length) * 100) / 100;
}

export async function getTopicAnalytics(
  topicId: string,
  lecturerId: string,
  category: FeedbackCategory | null,
  studentId: string | null,
): Promise<TopicAnalytics> {
  return withTransaction(async (client) => {
    const topic = await repo.findTopicClass(client, topicId);
    if (!topic) {
      throw notFound('TOPIC_NOT_FOUND', 'Topik tidak ditemukan.');
    }

    const allowed = await repo.isLecturerForClass(client, topic.class_id, lecturerId);
    if (!allowed) {
      throw forbidden('CLASS_FORBIDDEN', 'Anda bukan dosen kelas ini.');
    }

    if (studentId) {
      const student = await repo.findStudentInClass(client, topic.class_id, studentId);
      if (!student) {
        throw notFound('STUDENT_NOT_FOUND', 'Mahasiswa tidak ditemukan di kelas ini.');
      }
      if (
        student.vark_type === 'KINESTHETIC' ||
        (category !== null && student.vark_type !== category)
      ) {
        throw forbidden('VARK_MISMATCH', 'Kategori tidak sesuai dengan VARK mahasiswa.');
      }
    }

    const rows = await repo.listFeedbackAnalytics(client, {
      topicId,
      classId: topic.class_id,
      category,
      studentId,
    });

    const studentMap = new Map<string, StudentFeedback>();
    for (const row of rows) {
      if (!studentMap.has(row.student_id)) {
        studentMap.set(row.student_id, {
          studentId: row.student_id,
          fullName: row.full_name,
          loginId: row.login_id,
          vark: row.vark_type,
          feedback: { VISUAL: null, AURAL: null, READ_WRITE: null },
        });
      }
      if (row.feedback_vark !== null && row.value !== null) {
        studentMap.get(row.student_id)!.feedback[row.feedback_vark] = row.value;
      }
    }

    const students = [...studentMap.values()];
    const values: number[] = [];
    let respondentCount = 0;

    for (const student of students) {
      const selectedValues = category
        ? [student.feedback[category]].filter((value): value is number => value !== null)
        : CATEGORIES.map((item) => student.feedback[item]).filter(
            (value): value is number => value !== null,
          );
      values.push(...selectedValues);
      if (selectedValues.length > 0) respondentCount += 1;
    }

    const histogram = BUCKETS.map((bucket) => ({ bucket, count: 0 }));
    for (const value of values) {
      histogram[bucketIndex(value)]!.count += 1;
    }

    const kinestheticRows = await repo.listKinestheticAnalytics(client, {
      topicId,
      classId: topic.class_id,
    });
    const assessedScores = kinestheticRows
      .map((row) => row.score)
      .filter((score): score is number => score !== null);
    const notSubmittedCount = kinestheticRows.filter(
      (row) => row.submission_id === null,
    ).length;

    return {
      topicId,
      category,
      studentId,
      respondentCount,
      mean: roundedMean(values),
      histogram,
      notRespondedCount: Math.max(0, students.length - respondentCount),
      aggregation: 'mean of latest feedback per student-topic-category',
      kinesthetic: {
        assessedCount: assessedScores.length,
        meanScore: roundedMean(assessedScores),
        notSubmittedCount,
      },
      students,
    };
  });
}
