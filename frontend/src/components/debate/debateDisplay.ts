import { CATEGORY_LABELS, PRIORITY_LABELS } from "../../lib/taskUtils";
import type { DebateSpeaker, DebateUtterance, RemediationTask } from "../../types/analysis";

export type DebatePersonaView = {
  name: string;
  role: string;
  letter: string;
  color: string;
  bubble: string;
};

// Why: 役割の説明は、内部の呼び名（情報の番人など）ではなく、何をする係かで書く（#194 の確定事項3 / #139）。
export const DEBATE_PERSONAS: Record<DebateSpeaker, DebatePersonaView> = {
  ANALYST: { name: "アナリスト", role: "測定の事実だけを拾う係", letter: "A", color: "#4f46e5", bubble: "#eef2ff" },
  INNOVATOR: {
    name: "イノベーター",
    role: "独自の強みを活かすアイデアを出す係",
    letter: "I",
    color: "#7e22ce",
    bubble: "#faf5ff",
  },
  SKEPTIC: { name: "スケプティック", role: "競合の目線で弱点を突く係", letter: "S", color: "#c2410c", bubble: "#fff7ed" },
  DIRECTOR: { name: "ディレクター", role: "合意案と少数意見をまとめる係", letter: "D", color: "#047857", bubble: "#ecfdf5" },
};

export const DEBATE_FACILITATOR: DebatePersonaView = {
  name: "進行役",
  role: "議論の準備",
  letter: "進",
  color: "#64748b",
  bubble: "#f8fafc",
};

export const DEBATE_CARD_ORDER: readonly DebateSpeaker[] = ["ANALYST", "INNOVATOR", "SKEPTIC", "DIRECTOR"];

/** 議論は2ラウンド固定で、1ラウンドはアナリスト → イノベーター → スケプティック。最後にディレクターがまとめる。 */
export const DEBATE_SPEAKING_SEQUENCE: readonly DebateSpeaker[] = [
  "ANALYST",
  "INNOVATOR",
  "SKEPTIC",
  "ANALYST",
  "INNOVATOR",
  "SKEPTIC",
  "DIRECTOR",
];

export const DEBATE_STEP_LABELS = ["ラウンド1", "ラウンド2", "まとめ"] as const;

/** 発言がどの段（ラウンド1・ラウンド2・まとめ）に属するか。 */
export function debateStepOf(utterance: DebateUtterance): number {
  if (utterance.round === null) {
    return 2;
  }
  return utterance.round <= 1 ? 0 : 1;
}

export function debateReplyLabel(utterance: DebateUtterance): string | null {
  if (utterance.replyTo === null) {
    return null;
  }
  const name = DEBATE_PERSONAS[utterance.replyTo].name;
  switch (utterance.stance) {
    case "REBUT":
      return `${name}への反論`;
    case "AGREE":
      return `${name}に賛成`;
    case "CONDITIONAL_AGREE":
      return "条件つきで賛成";
    case "CONFIRM":
      return `${name}の指摘を確認`;
    case "RESPOND":
      return `${name}への応答`;
    default:
      return `${name}への返事`;
  }
}

const MEASUREMENT_EVIDENCE_LABELS: Record<string, string> = {
  QUERY_MENTIONS: "質問ごとの言及",
  COMPETITORS: "回答によく出た他社",
  SITE_DIAGNOSIS: "サイト診断",
};

export type DebateEvidenceView = { heading: string; body: string };

/**
 * Why: 改善タスクの根拠は番号だけで届く。番号だけでは何の対策かわからないので、画面の改善タスクと同じ
 * タイトルとラベルを添える。番号は、改善タスクの一覧の並び順（サーバーで確定済み）に対応する（#141）。
 */
export function debateEvidenceOf(utterance: DebateUtterance, tasks: RemediationTask[]): DebateEvidenceView | null {
  if (utterance.evidenceKind === "REMEDIATION_TASK" && utterance.evidenceTaskNumber !== null) {
    const task = tasks[utterance.evidenceTaskNumber - 1];
    const body =
      task !== undefined
        ? `${task.title}（${PRIORITY_LABELS[task.priority]}・${CATEGORY_LABELS[task.category]}）`
        : utterance.evidenceDetail;
    return { heading: `根拠：改善タスク ${utterance.evidenceTaskNumber}`, body };
  }
  const label = utterance.evidenceKind !== null ? MEASUREMENT_EVIDENCE_LABELS[utterance.evidenceKind] : undefined;
  if (label === undefined || utterance.evidenceDetail.length === 0) {
    return null;
  }
  return { heading: `根拠：測定結果（${label}）`, body: utterance.evidenceDetail };
}

export type DebateItem = {
  key: string;
  persona: DebatePersonaView;
  /** null は進行役（AI の発言ではなく、画面が出す最初の一言）。 */
  speaker: DebateSpeaker | null;
  step: number;
  text: string;
  reply: string | null;
  evidence: DebateEvidenceView | null;
};

// Why: 要約が空の発言（AI が形の崩れた応答を返したとき）は、出す文が無いので並べない（ADR-094 の申し送り）。
export function buildDebateItems(utterances: DebateUtterance[], tasks: RemediationTask[]): DebateItem[] {
  const intro =
    tasks.length > 0
      ? `測定結果と、改善タスク${tasks.length}件を読み込みました。何から、いつまでに取り組むかを話し合います。`
      : "測定結果を読み込みました。何から、いつまでに取り組むかを話し合います。";
  const items: DebateItem[] = [
    { key: "intro", persona: DEBATE_FACILITATOR, speaker: null, step: 0, text: intro, reply: null, evidence: null },
  ];
  utterances.forEach((utterance, index) => {
    if (utterance.summary.length === 0) {
      return;
    }
    items.push({
      key: `utterance-${index}`,
      persona: DEBATE_PERSONAS[utterance.speaker],
      speaker: utterance.speaker,
      step: debateStepOf(utterance),
      text: utterance.summary,
      reply: debateReplyLabel(utterance),
      evidence: debateEvidenceOf(utterance, tasks),
    });
  });
  return items;
}
