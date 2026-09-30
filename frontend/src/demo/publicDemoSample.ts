import type {
  AiRecognitionSummary,
  CompetitorShare,
  ContentEvidenceItem,
  DebateUtterance,
  JobMinorityReport,
  JobRoadmapItem,
  RemediationTask,
  ScoreBreakdown,
} from "../types/analysis";

/*
 * 公開デモ（`/demo`）の見本データ。架空の工務店の解析結果として、本番の結果画面と同じ部品に流す（#188）。
 *
 * Why: 見本の中身どうし（社名が出た問数・診断の所見・改善タスク・ロードマップ・総合診断）が食い違うと、
 * 見る人に作り物だと伝わる。1つの会社の状況から組み立て、数字は診断10項目の判定から積み上げてある。
 * 実在の会社と誤解されないよう、他社名は「A社」などにし、画面には見本である旨を必ず出す。
 */

export const DEMO_DEFAULT_BRAND = "あなたのブランド";

export const DEMO_DIAGNOSTIC =
  "地名が入った質問ではAIに紹介されていますが、地名なしの質問では競合2社に押されています。" +
  "トップページで何の会社かが一文で伝わらず、よくある質問への答えもないため、AIは競合のページを選んでいます。";

/** 回答に社名が出た割合（SoM）。10問中2問で、出た回でも上位ではない。 */
export const DEMO_SOM_SCORE = 18.0;

export const DEMO_QUERIES = [
  "自然素材 注文住宅 工務店 おすすめ",
  "子育て 家 間取り 相談できる工務店",
  "注文住宅 工務店 ハウスメーカー 違い",
  "耐震等級3 工務店 選び方",
  "地元の杉 家 建てる",
  "注文住宅 費用 目安",
  "工務店 完成見学会 行くべき",
  "注文住宅 失敗しない 工務店",
  "木の家 メンテナンス 費用",
  "地域密着 工務店 評判",
];

export const DEMO_AI_RECOGNITION: AiRecognitionSummary = {
  dominant: "UNKNOWN",
  recognizedCount: 2,
  misidentifiedCount: 0,
  unknownCount: 8,
  evaluatedCount: 10,
};

export function demoCompetitorShares(brand: string): CompetitorShare[] {
  return [
    { label: brand, share: 18.0, self: true },
    { label: "A社", share: 35.0, self: false },
    { label: "B社", share: 27.0, self: false },
    { label: "C社", share: 12.5, self: false },
    { label: "D社", share: 7.5, self: false },
  ];
}

/** 診断10項目。満たす=5点・一部=2.5点・なし=0点で、合計が内容の軸（25.0/50）になる。 */
export const DEMO_CONTENT_EVIDENCE: ContentEvidenceItem[] = [
  { criterionId: "DIRECT_ANSWER_FIRST", verdict: "NO", score: 0, maxScore: 5,
    evidence: "トップページの冒頭は「秋の完成見学会のお知らせ」で、何の会社かが書かれていない" },
  { criterionId: "ATOMIC_FACTS", verdict: "PARTIAL", score: 2.5, maxScore: 5,
    evidence: "「創業1978年」「年間約20棟」はあるが、大工の人数や経験年数の記載がない" },
  { criterionId: "SOLUTION_SCENARIOS", verdict: "YES", score: 5, maxScore: 5,
    evidence: "施工事例が12件、家族構成と間取りの写真つきで載っている" },
  { criterionId: "VERIFIABLE_AUTHORITY", verdict: "PARTIAL", score: 2.5, maxScore: 5,
    evidence: "「耐震等級3が標準」とあるが、根拠となる資料へのリンクがない" },
  { criterionId: "FAQ_PRESENCE", verdict: "NO", score: 0, maxScore: 5,
    evidence: "よくある質問のページが見当たらない" },
  { criterionId: "NUMBERED_PROCESS_FLOW", verdict: "YES", score: 5, maxScore: 5,
    evidence: "「家づくりの流れ」が7つの手順で、番号付きで書かれている" },
  { criterionId: "ENTITY_BIOGRAPHY", verdict: "PARTIAL", score: 2.5, maxScore: 5,
    evidence: "代表のあいさつはあるが、経歴や資格の記載がない" },
  { criterionId: "LOCAL_CONTEXT", verdict: "YES", score: 5, maxScore: 5,
    evidence: "地元の杉を使うことと、施工する地域が明記されている" },
  { criterionId: "PRICE_AND_CONSTRAINTS", verdict: "NO", score: 0, maxScore: 5,
    evidence: "価格の目安や、対応できない工事の記載がない" },
  { criterionId: "EXTERNAL_CITATIONS", verdict: "PARTIAL", score: 2.5, maxScore: 5,
    evidence: "地域の住宅雑誌への掲載が1件だけ紹介されている" },
];

export const DEMO_TECHNICAL_EVIDENCE =
  "Schema.org: 未実装、見出しの階層: 適切、robots.txt: あり、llms.txt: なし、ページの表示速度: 標準";

/** 内容 25.0 + 構造 12.0 + 権威 9.5（第三者言及 7.5・地域の評判 2.0）= 46.5 */
export const DEMO_SCORE_BREAKDOWN: ScoreBreakdown = {
  // 旧モデル（後方互換・表示には使わない）
  aiAuditTotal: 25.0,
  meoTotal: 2.0,
  machineReadabilityTotal: 12.0,
  finalScore: 46.5,
  contentTotal: 25.0,
  technicalTotal: 12.0,
  authorityTotal: 9.5,
  authorityThirdPartyCore: 7.5,
  authorityLocalMeoSub: 2.0,
  authorityWikipediaKgBonus: 0,
  calculationVersion: "V13_GEO4AXIS",
};

/**
 * 改善タスク。本番と同じく「効果の大きい順、同じ効果の中はすぐ直せる順」に並べてある。
 * STANDARD プランとして見せるので、「効果 大」だけ本文と根拠を伏せる（本番の RemediationPriorityLevel と同じ）。
 */
export const DEMO_REMEDIATION_TASKS: RemediationTask[] = [
  {
    id: "demo-task-1",
    category: "SPIKE",
    priority: "S",
    title: "トップページの冒頭で、何の会社かを一文で伝える",
    content: "",
    impactScore: 0.9,
    rationale: "AIは冒頭の文から会社の特徴を読み取ります。ここが季節のお知らせだと、何の会社として紹介すればよいか判断できません。",
    evidence: null,
    level: 3,
    requiresProPlan: true,
    isMasked: true,
  },
  {
    id: "demo-task-2",
    category: "SLAB",
    priority: "S",
    title: "施工事例に「なぜこの間取りにしたか」を書き足す",
    content: "",
    impactScore: 0.8,
    rationale: "間取りを家族ごとに変えていることは、競合との違いになります。理由まで書くと、AIが紹介文に使いやすくなります。",
    evidence: null,
    level: 3,
    requiresProPlan: true,
    isMasked: true,
  },
  {
    id: "demo-task-3",
    category: "SPIKE",
    priority: "A",
    title: "よくある質問のページを作る",
    content: "工期・費用の目安・保証の年数・見学会の申し込み方など、相談の前によく聞かれることを10問ほど、問いと答えの形で載せます。",
    impactScore: 0.6,
    rationale: "AIは質問に答えるとき、問いと答えの形で書かれたページを参考にしやすくなります。",
    evidence: "よくある質問のページが見当たらない",
    level: 2,
    requiresProPlan: false,
    isMasked: false,
  },
  {
    id: "demo-task-4",
    category: "SPIKE",
    priority: "A",
    title: "会社概要に、大工の人数と経験年数を書く",
    content: "「自社の大工 6名（平均経験年数 18年）」のように、数字で書きます。",
    impactScore: 0.5,
    rationale: "数字があると、AIは「職人が自社にいる会社」と言い切れるようになります。",
    evidence: "「創業1978年」「年間約20棟」はあるが、大工の人数や経験年数の記載がない",
    level: 2,
    requiresProPlan: false,
    isMasked: false,
  },
  {
    id: "demo-task-5",
    category: "SLAB",
    priority: "B",
    title: "地域の住宅情報サイトに、掲載を依頼する",
    content: "地域の住宅情報サイトや、工務店を紹介する記事に掲載を依頼し、自社サイトから掲載先を紹介します。",
    impactScore: 0.3,
    rationale: "自社以外のサイトで紹介されていると、AIが信頼できる会社と判断する材料になります。",
    evidence: "地域の住宅雑誌への掲載が1件だけ紹介されている",
    level: 1,
    requiresProPlan: false,
    isMasked: false,
  },
];

export const DEMO_ROADMAP: JobRoadmapItem[] = [
  {
    phase: "NOW",
    phaseLabel: "今すぐ",
    title: "トップページで、何の会社かを伝える",
    rationale: "AIが会社を読み取れないうちは、ほかの改善が効きません。",
    expectedImpact: "地名なしの質問でも社名が出る下地ができる",
    taskRange: { first: 1, last: 1 },
  },
  {
    phase: "SHORT_TERM",
    phaseLabel: "1〜3ヶ月",
    title: "代表的な施工事例とよくある質問を整える",
    rationale: "競合がAIに選ばれているのは、よくある質問のページです。事例は代表の3件に絞り、負担を抑えます。",
    expectedImpact: "競合に取られている質問で選ばれるようになる",
    taskRange: { first: 2, last: 3 },
  },
  {
    phase: "MID_TERM",
    phaseLabel: "3〜6ヶ月",
    title: "強みを数字と第三者の記事で裏づける",
    rationale: "大工の人数や、ほかのサイトでの紹介があると、AIが強みを言い切れます。",
    expectedImpact: "AIが強みを具体的に紹介するようになる",
    taskRange: { first: 4, last: 5 },
  },
];

export const DEMO_MINORITY_REPORTS: JobMinorityReport[] = [
  {
    insight: "施工事例12件すべてに「なぜこの間取りにしたか」を書き足してから公開する",
    conflictReason: "3か月以上かかり、その間トップページで何の会社かが伝わらない状態が続くため。",
    evidence: "地名なしの質問8問で、社名が一度も出ていない（測定結果）",
  },
];

/**
 * 4人のAIの議論の見本（#234）。本番と同じ形（2ラウンド×3人＋まとめ役、要約は120文字以内）にしてある。
 *
 * Why: 議論の結論が、上の改善ロードマップ（今すぐ1／1〜3ヶ月 2〜3／3〜6ヶ月 4〜5）と少数意見に
 * そのままつながるようにした。根拠は本番で出せる種類だけを使う（AIが引用したページは、まだ測っていない）。
 */
export const DEMO_DEBATE_UTTERANCES: DebateUtterance[] = [
  {
    round: 1,
    speaker: "ANALYST",
    summary:
      "測定の事実です。AIに聞いた10の質問のうち、社名が出たのは2問だけでした。出た2問はどちらも地名が入った質問で、地名なしの8問では一度も出ていません。",
    replyTo: null,
    stance: null,
    evidenceKind: "QUERY_MENTIONS",
    evidenceTaskNumber: null,
    evidenceDetail: "地名あり 2問中2問で言及 ／ 地名なし 8問中0問",
  },
  {
    round: 1,
    speaker: "INNOVATOR",
    summary:
      "本命は改善タスク2です。施工事例ごとに「なぜこの間取りにしたか」を書けば、地名なしの質問でもAIが紹介しやすくなります。3か月かけて全12件に書き足しましょう。",
    replyTo: null,
    stance: null,
    evidenceKind: "REMEDIATION_TASK",
    evidenceTaskNumber: 2,
    evidenceDetail: "",
  },
  {
    round: 1,
    speaker: "SKEPTIC",
    summary:
      "3か月は長すぎます。その間、トップページで何の会社かすら伝わらない状態が続きます。改善タスク1を今すぐ終えるべきです。12件すべてに書き足すのも、この規模の会社には重すぎます。",
    replyTo: "INNOVATOR",
    stance: "REBUT",
    evidenceKind: null,
    evidenceTaskNumber: null,
    evidenceDetail: "",
  },
  {
    round: 2,
    speaker: "ANALYST",
    summary:
      "確認しました。社名が出なかった8問のうち6問で、AIが紹介していたのはA社かB社でした。サイト診断では、よくある質問のページが見当たりませんでした。",
    replyTo: "SKEPTIC",
    stance: "CONFIRM",
    evidenceKind: "COMPETITORS",
    evidenceTaskNumber: null,
    evidenceDetail: "A社 6問 ／ B社 5問",
  },
  {
    round: 2,
    speaker: "INNOVATOR",
    summary:
      "案を直します。改善タスク1は今すぐ。タスク2は代表的な3件に絞り、タスク3のよくある質問と合わせて1〜3か月で進めます。競合が答えている質問に、こちらも答えられるようにします。",
    replyTo: "SKEPTIC",
    stance: "RESPOND",
    evidenceKind: "REMEDIATION_TASK",
    evidenceTaskNumber: 3,
    evidenceDetail: "",
  },
  {
    round: 2,
    speaker: "SKEPTIC",
    summary:
      "その割り振りなら賛成です。ただ、よくある質問に価格の目安を書けるかは、この会社しだいです。書けない場合の答え方も用意しておくべきです。",
    replyTo: "INNOVATOR",
    stance: "CONDITIONAL_AGREE",
    evidenceKind: null,
    evidenceTaskNumber: null,
    evidenceDetail: "",
  },
  {
    round: null,
    speaker: "DIRECTOR",
    summary:
      "まとめます。今すぐ改善タスク1、1〜3か月でタスク2と3（事例は代表3件に絞る）、3〜6か月でタスク4と5。「全事例に書き足してから公開する」案は時間がかかるため、少数意見として残します。",
    replyTo: null,
    stance: null,
    evidenceKind: null,
    evidenceTaskNumber: null,
    evidenceDetail: "",
  },
];
