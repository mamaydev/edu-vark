import type { PoolClient } from 'pg';

export interface AuditInput {
  actorId: string | null;
  action: string;
  entity: string;
  entityId?: string | null;
  metadata?: Record<string, unknown>;
}

export async function writeAudit(client: PoolClient, input: AuditInput): Promise<void> {
  await client.query(
    `
      INSERT INTO audit_logs (actor_id, action, entity, entity_id, metadata)
      VALUES ($1, $2, $3, $4, $5::jsonb)
    `,
    [
      input.actorId,
      input.action,
      input.entity,
      input.entityId ?? null,
      JSON.stringify(input.metadata ?? {}),
    ],
  );
}
