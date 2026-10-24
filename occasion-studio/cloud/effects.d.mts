export const EFFECTS: ReadonlyArray<{ id: string; label: string; group: string }>;
export function normaliseEffect(effect: string): string;
export function validEffect(effect: unknown): boolean;
