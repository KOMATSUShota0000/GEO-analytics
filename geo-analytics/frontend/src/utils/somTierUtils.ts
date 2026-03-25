export type SomTierKey = "invisible" | "challenger" | "competitive" | "marketLeader";

export interface SomTierInfo {
  tierKey: SomTierKey;
  title: string;
  subtitle: string;
  accentClass: string;
  ringClass: string;
  advice: string;
  minInclusive: number;
  maxInclusive: number;
}

const MARKET_LEADER: SomTierInfo = {
  tierKey: "marketLeader",
  title: "Market Leader",
  subtitle: "市場リーダー",
  accentClass: "bg-amber-100 text-amber-900 border-amber-300",
  ringClass: "text-amber-500",
  advice:
    "カテゴリを代表するエンティティとしてAIに深く浸透しています。競合によるコンテキストのハイジャックを防ぐため、複数フォーマットでの継続的な情報発信と、スコアの常時監視（防衛的GEO）を継続してください。",
  minInclusive: 31,
  maxInclusive: 100,
};

const COMPETITIVE: SomTierInfo = {
  tierKey: "competitive",
  title: "Competitive",
  subtitle: "競争力あり",
  accentClass: "bg-indigo-100 text-indigo-800 border-indigo-300",
  ringClass: "text-indigo-500",
  advice:
    "主要な選択肢として認知されています。独自データや業界レポートの発行、専門家の見解（Expert Citations）を追加し、AIに引用されやすい『データノード』を構築して露出度を最大化しましょう。",
  minInclusive: 16,
  maxInclusive: 30,
};

const CHALLENGER: SomTierInfo = {
  tierKey: "challenger",
  title: "Challenger",
  subtitle: "チャレンジャー",
  accentClass: "bg-blue-100 text-blue-800 border-blue-300",
  ringClass: "text-blue-500",
  advice:
    "競合との間に『信頼の差（Trust Gap）』が存在します。G2やCapterraなどのサードパーティレビューサイトでの高評価獲得や、UGC（ユーザー生成コンテンツ）の拡充を推進してください。",
  minInclusive: 6,
  maxInclusive: 15,
};

const INVISIBLE: SomTierInfo = {
  tierKey: "invisible",
  title: "Invisible",
  subtitle: "存在なし",
  accentClass: "bg-slate-100 text-slate-800 border-slate-300",
  ringClass: "text-slate-500",
  advice:
    "AIの学習データにブランドのシグナルが欠落しています。llms.txtの設置やSchemaマークアップによるTechnical GEO、およびデジタルPRを通じた権威あるメディアでの言及獲得を急いでください。",
  minInclusive: 0,
  maxInclusive: 5,
};

export function getSomTierInfo(score: number): SomTierInfo {
  const s = Math.max(0, Math.min(100, Number.isFinite(score) ? score : 0));
  if (s >= MARKET_LEADER.minInclusive) {
    return MARKET_LEADER;
  }
  if (s >= COMPETITIVE.minInclusive) {
    return COMPETITIVE;
  }
  if (s >= CHALLENGER.minInclusive) {
    return CHALLENGER;
  }
  return INVISIBLE;
}

export function somScoreProgressRatio(score: number): number {
  const s = Math.max(0, Math.min(100, Number.isFinite(score) ? score : 0));
  return s / 100;
}
