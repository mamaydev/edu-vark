import { withTransaction } from '../../db/transaction';
import { forbidden, notFound } from '../../lib/errors';
import * as repo from './analytics.repository';

export type FeedbackCategory = repo.FeedbackCategory;

export interface HistogramBucket {
  bucket: string;
  count: number;
}

export interface StudentFeedback {
  studentId: string;
  fullName: string;
  loginId: string;
  /**
   * Kategori asal yang relevan untuk baris ini.
   * - Jika `category` filter diisi: kategori tersebut bila mahasiswa punya nilai
   *   tersimpan pada kategori itu, selain itu `null`.
   * - Jika tanpa filter: kategori asal nilai feedback terbaru, atau `null` bila
   *   mahasiswa belum pernah memberi feedback pada topik ini.
   *
   * Nilai `null` berarti "belum memberi feedback" dan TIDAK sama dengan 0%.
   */
  vark: FeedbackCategory | null;
  /** Semua kategori asal yang memiliki nilai feedback tersimpan pada topik ini. */
  originCategories: FeedbackCategory[];
  /** Nilai feedback terbaru per kategori asal; `null` = belum memberi feedback. */
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
  definitions: Record<string, string>;
  kinesthetic: {
    assessedCount: number;
    meanScore: number | null;
    notSubmittedCount: number;
  };
  students: StudentFeedback[];
}

const CATEGORIES: FeedbackCategory[] = ['VISUAL', 'AURAL', 'READ_WRITE'];
const BUCKETS = ['0-20', '21-40', '41-60', '61-80', '81-100'];

const AGGREGATION_DEFINITION =
  'Rerata aritmetika feedback terbaru per mahasiswa-topik-kategori (Visual/Audio/Read-Write). ' +
  'Mahasiswa tanpa feedback tidak dihitung sebagai nilai 0%.';

const DEFINITIONS: Record<string, string> = {
  respondentCount:
    'Jumlah mahasiswa yang memiliki nilai feedback tersimpan pada cakupan filter.',
  notRespondedCount:
    'Field kompatibilitas dengan definisi existing: jumlah mahasiswa pada populasi respons ' +
    '(enrollment ACTIVE di kelas, memiliki feedback untuk filter ATAU sesi aktif terakhir ' +
    'Visual/Audio/Read-Write yang cocok filter) yang belum memiliki feedback pada filter itu. ' +
    'Sesi Kinesthetic tidak masuk populasi ini. Berbeda dari nilai 0%; FR-048 (status individu) terpisah.',
  mean:
    'Rerata nilai feedback pada cakupan filter. Nilai 0% tetap dihitung; mahasiswa tanpa feedback tidak.',
  kinestheticAssessedCount:
    'Jumlah seluruh penilaian final Kinesthetic yang tersimpan pada studi kasus topik ini, ' +
    'termasuk setelah logout, pergantian VARK, dan enrollment LEFT.',
  kinestheticMeanScore:
    'Rerata seluruh nilai final Kinesthetic pada studi kasus topik ini; tidak digabung dengan feedback V/A/R.',
  kinestheticNotSubmittedCount:
    'Field kompatibilitas: jumlah mahasiswa bersesi Kinesthetic aktif yang belum mengirim ' +
    'jawaban studi kasus topik ini. Bukan status individu FR-048.',
};

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

interface StoredValue {
  value: number;
  updatedAt: Date;
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

    const population = await repo.listClassStudents(client, {
      classId: topic.class_id,
      studentId,
    });
    if (studentId && population.length === 0) {
      throw notFound('STUDENT_NOT_FOUND', 'Mahasiswa tidak ditemukan di kelas ini.');
    }

    const storedRows = await repo.listStoredFeedback(client, { topicId, studentId });
    const storedByStudent = new Map<string, Map<FeedbackCategory, StoredValue>>();
    for (const row of storedRows) {
      let byCategory = storedByStudent.get(row.student_id);
      if (!byCategory) {
        byCategory = new Map();
        storedByStudent.set(row.student_id, byCategory);
      }
      byCategory.set(row.vark_type, { value: row.value, updatedAt: row.updated_at });
    }

    const students: StudentFeedback[] = population.map((student) => {
      const stored = storedByStudent.get(student.student_id);
      const feedback: Record<FeedbackCategory, number | null> = {
        VISUAL: null,
        AURAL: null,
        READ_WRITE: null,
      };
      const originCategories: FeedbackCategory[] = [];
      let latestOrigin: FeedbackCategory | null = null;
      let latestAt = Number.NEGATIVE_INFINITY;

      for (const item of CATEGORIES) {
        const entry = stored?.get(item);
        if (!entry) continue;
        feedback[item] = entry.value;
        originCategories.push(item);
        const timestamp = entry.updatedAt.getTime();
        if (timestamp > latestAt) {
          latestAt = timestamp;
          latestOrigin = item;
        }
      }

      // Filter historis: label VARK mengikuti kategori asal nilai tersimpan,
      // bukan kategori VARK sesi/login terakhir mahasiswa.
      const vark =
        category !== null ? (feedback[category] !== null ? category : null) : latestOrigin;

      return {
        studentId: student.student_id,
        fullName: student.full_name,
        loginId: student.login_id,
        vark,
        originCategories,
        feedback,
      };
    });

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

    const kinestheticScores = await repo.listKinestheticScores(client, topicId);
    const scores = kinestheticScores.map((row) => row.score);
    const notSubmittedCount = await repo.countKinestheticNotSubmitted(client, {
      topicId,
      classId: topic.class_id,
    });

    // Field kompatibilitas: populasi dan semantik existing dipertahankan,
    // terpisah dari statistik kategori asal FR-029–033 di atas.
    const legacyPopulation = await repo.listLegacyFeedbackPopulation(client, {
      topicId,
      classId: topic.class_id,
      category,
      studentId,
    });

    return {
      topicId,
      category,
      studentId,
      respondentCount,
      mean: roundedMean(values),
      histogram,
      notRespondedCount: Math.max(0, legacyPopulation.length - respondentCount),
      aggregation: AGGREGATION_DEFINITION,
      definitions: DEFINITIONS,
      kinesthetic: {
        assessedCount: scores.length,
        meanScore: roundedMean(scores),
        notSubmittedCount,
      },
      students,
    };
  });
}
