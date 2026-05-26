export function isMaintenancePhase(score: number | undefined | null): boolean {
  return typeof score === "number" && Number.isFinite(score) && score >= 90;
}
