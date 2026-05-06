const EEAT = new Set(["VERIFIABLE_AUTHORITY", "ENTITY_BIOGRAPHY", "EXTERNAL_CITATIONS"]);

export type EmotionalAlert = {
  severity: "error" | "warning";
  message: string;
};

export function buildEmotionalAlerts(gaps: string[], industryType: string): EmotionalAlert[] {
  const ymyl = industryType === "YMYL";
  const out: EmotionalAlert[] = [];
  for (let i = 0; i < gaps.length; i++) {
    const id = gaps[i];
    const danger = ymyl && EEAT.has(id);
    const severity = danger ? "error" : "warning";
    const message = `⚠️【機会損失の警告】${id} で競合に先行されています。AIからの推薦枠を奪われかねません。`;
    out.push({ severity, message });
  }
  return out;
}
