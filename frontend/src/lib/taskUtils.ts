import type {
  RemediationTask,
  RemediationTaskCategory,
  RemediationTaskPriority,
} from "../types/analysis";
import { criterionLabel } from "./rubricLabels";

// Why: 内部名（SPIKE/SLAB、S/A/B）は利用者に意味が通じない。区別の中身（かかる時間・効果の大きさ）で呼ぶ（#139）。
export const CATEGORY_LABELS: Record<RemediationTaskCategory, string> = {
  SPIKE: "すぐ直せる",
  SLAB: "時間がかかる",
};

export const PRIORITY_LABELS: Record<RemediationTaskPriority, string> = {
  S: "効果 大",
  A: "効果 中",
  B: "効果 小",
};

const CATEGORY_ORDER: readonly RemediationTaskCategory[] = ["SPIKE", "SLAB"];

const CATEGORY_GROUP_TEXT: Record<RemediationTaskCategory, { heading: string; note: string }> = {
  SPIKE: {
    heading: "まず取り組む：すぐ直せる対策",
    note: "既存ページへの書き足し・書き直しなど、数時間で終わる作業です。",
  },
  SLAB: {
    heading: "次に取り組む：時間がかかる対策",
    note: "新しいページの制作や社内の体制づくりなど、計画を立てて進める作業です。",
  },
};

function getPriorityRank(priority: string): number {
  if (priority === "S") {
    return 1;
  }
  if (priority === "A") {
    return 2;
  }
  if (priority === "B") {
    return 3;
  }
  return 99;
}

/**
 * Why: 旧実装は効果（S/A/B）だけで並べていたため、数時間で終わる作業と数か月かかる作業が交互に並び、
 * 何から手をつければよいかが読み取れなかった（#139）。すぐ直せるものを先に、その中を効果の大きい順にする。
 * Pro未満で本文が伏せられるタスクも同じ位置に置き、プランによって番号が変わらないようにする（オーナー確定 2026-09-22）。
 */
function compareTasks(a: RemediationTask, b: RemediationTask): number {
  const ca = CATEGORY_ORDER.indexOf(a.category);
  const cb = CATEGORY_ORDER.indexOf(b.category);
  if (ca !== cb) {
    return ca - cb;
  }
  const ra = getPriorityRank(a.priority);
  const rb = getPriorityRank(b.priority);
  if (ra !== rb) {
    return ra - rb;
  }
  if (b.impactScore !== a.impactScore) {
    return b.impactScore - a.impactScore;
  }
  return a.id.localeCompare(b.id);
}

export type NumberedTask = {
  number: number;
  task: RemediationTask;
};

export type TaskEffortGroup = {
  category: RemediationTaskCategory;
  heading: string;
  note: string;
  tasks: NumberedTask[];
};

export function hasLockedRemediationTasks(tasks: RemediationTask[]): boolean {
  if (!Array.isArray(tasks) || tasks.length === 0) {
    return false;
  }
  return tasks.some((t) => t.isMasked === true);
}

/** 番号はグループをまたいだ通し番号にする。「上から番号順に進める」をそのまま指示として読めるようにするため。 */
export function groupTasksForDisplay(tasks: RemediationTask[]): TaskEffortGroup[] {
  if (!Array.isArray(tasks) || tasks.length === 0) {
    return [];
  }
  const sorted = [...tasks].sort((a, b) => compareTasks(a, b));
  const out: TaskEffortGroup[] = [];
  for (let i = 0; i < sorted.length; i++) {
    const task = sorted[i];
    let group = out.length > 0 ? out[out.length - 1] : undefined;
    if (group === undefined || group.category !== task.category) {
      group = { category: task.category, ...CATEGORY_GROUP_TEXT[task.category], tasks: [] };
      out.push(group);
    }
    group.tasks.push({ number: i + 1, task });
  }
  return out;
}

const LEGACY_EVIDENCE_KEY = /(criterionId|self_status|self_evidence|competitor_yes_evidence)=/g;

/**
 * Why: #139 以前のプロンプトでは、AI が入力の見出し（`criterionId=... self_status=NO`）を根拠欄へそのまま写していた。
 * 保存済みのタスクは再生成しないため、表示時にだけ日本語へ直す。新しい形式の根拠はそのまま返す。
 */
export function humanizeEvidence(raw: string): string {
  const text = raw.trim();
  if (!text.startsWith("criterionId=")) {
    return text;
  }
  const matches = [...text.matchAll(LEGACY_EVIDENCE_KEY)];
  const fields: Record<string, string> = {};
  for (let i = 0; i < matches.length; i++) {
    const m = matches[i];
    const start = (m.index ?? 0) + m[0].length;
    const end = i + 1 < matches.length ? (matches[i + 1].index ?? text.length) : text.length;
    fields[m[1]] = text.slice(start, end).replace(/,\s*$/, "").trim();
  }
  const parts: string[] = [];
  const criterion = fields.criterionId ?? "";
  const status = fields.self_status;
  const statusText =
    status === "NO" ? "記載がありません" : status === "PARTIAL" ? "一部しか記載がありません" : undefined;
  const selfQuote = fields.self_evidence ? `（「${fields.self_evidence}」）` : "";
  parts.push(
    statusText !== undefined
      ? `診断項目「${criterionLabel(criterion)}」：自社サイトには${statusText}${selfQuote}。`
      : `診断項目「${criterionLabel(criterion)}」${selfQuote}。`,
  );
  if (fields.competitor_yes_evidence) {
    parts.push(`競合サイトには「${fields.competitor_yes_evidence}」と書かれています。`);
  }
  return parts.join("");
}

function markdownToPlainText(markdown: string): string {
  if (typeof markdown !== "string" || markdown.length === 0) {
    return "";
  }
  return markdown
    .replace(/\r\n?/g, "\n")
    .replace(/^#{1,6}\s*/gm, "")
    .replace(/\*\*(.+?)\*\*/g, "$1")
    .replace(/`([^`]*)`/g, "$1")
    .replace(/[ \t]+$/gm, "")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

/**
 * Why: 旧実装は空白と改行をすべて1つの空白に潰していたため、手順が1行につながり提案書へ貼れなかった（#139）。
 * 本文から「なぜ必要か」を外して rationale に一本化したので、理由と根拠もコピーに含める。
 */
export function buildClipboardText(task: RemediationTask, number?: number): string {
  const prefix = number !== undefined ? `${number}. ` : "";
  const head = `${prefix}${task.title.trim()}（${CATEGORY_LABELS[task.category]}・${PRIORITY_LABELS[task.priority]}）`;
  if (task.isMasked === true) {
    return head;
  }
  const sections: string[] = [head];
  const body = markdownToPlainText(task.content);
  if (body.length > 0) {
    sections.push(`やること\n${body}`);
  }
  const rationale = task.rationale?.trim();
  if (rationale) {
    sections.push(`なぜ効くか\n${rationale}`);
  }
  const evidence = task.evidence ? humanizeEvidence(task.evidence) : "";
  if (evidence.length > 0) {
    sections.push(`根拠\n${evidence}`);
  }
  return sections.join("\n\n");
}
