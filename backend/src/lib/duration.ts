const UNIT_MS: Record<string, number> = {
  ms: 1,
  s: 1000,
  m: 60 * 1000,
  h: 60 * 60 * 1000,
  d: 24 * 60 * 60 * 1000,
};

/**
 * Parses the compact durations used by the environment (`15m`, `12h`, `8h`,
 * `30s`) into milliseconds. Invalid values fall back to the default so a typo
 * cannot silently create a zero-length session.
 */
export function parseDurationMs(value: string | undefined, fallback: string): number {
  const raw = (value ?? fallback).trim();
  const match = /^(\d+)(ms|s|m|h|d)$/.exec(raw);
  if (!match) return parseDurationMs(fallback, fallback);
  const amount = Number(match[1]);
  const unit = match[2]!;
  return amount * UNIT_MS[unit]!;
}

export function parseDurationSeconds(value: string | undefined, fallback: string): number {
  return Math.floor(parseDurationMs(value, fallback) / 1000);
}
