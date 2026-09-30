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
 * 公開デモ（`/demo`）の見本データ。架空の会社（中小企業向けの勤怠管理のクラウド）の解析結果として、
 * 本番の結果画面と同じ部品に流す（#188）。解析の依頼で「企業向け・専門サービス」を選んだ想定（#236）。
 *
 * Why: 見本の中身どうし（社名が出た問数・診断の所見・改善タスク・ロードマップ・総合診断）が食い違うと、
 * 見る人に作り物だと伝わる。1つの会社の状況から組み立て、数字は診断10項目の判定から積み上げてある。
 * 実在の会社と誤解されないよう、他社名は「A社」などにし、画面には見本である旨を必ず出す。
 */

export const DEMO_DEFAULT_BRAND = "あなたのブランド";

/** 解析の依頼で選ぶ事業の種類。見本は「企業向け・専門サービス」（BtoB・受託・SaaS）の想定（#236）。 */
export const DEMO_INDUSTRY_MODE = "CORPORATE_SERVICE";

export const DEMO_DIAGNOSTIC =
  "シフト勤務に触れた質問ではAIに紹介されていますが、比較や料金を聞いた質問では競合2社に押されています。" +
  "トップページで何のサービスかが一文で伝わらず、よくある質問への答えもないため、AIは競合のページを選んでいます。";

/** 回答に社名が出た割合（SoM）。10問中2問で、出た回でも上位ではない。 */
export const DEMO_SOM_SCORE = 18.0;

export const DEMO_QUERIES = [
  "勤怠管理 システム おすすめ 中小企業",
  "勤怠管理 クラウド 比較",
  "シフト制 勤怠管理 アプリ",
  "飲食店 シフト 勤怠 まとめて管理",
  "勤怠管理 システム 料金 相場",
  "タイムカード やめる 方法",
  "残業時間 自動集計 ツール",
  "有給休暇 管理 システム",
  "勤怠管理 給与計算 連携",
  "勤怠管理 システム 導入 失敗しない",
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
    evidence: "トップページの冒頭は「働き方を、もっと自由に。」で、何のサービスかが書かれていない" },
  { criterionId: "ATOMIC_FACTS", verdict: "PARTIAL", score: 2.5, maxScore: 5,
    evidence: "「導入企業1,200社」はあるが、利用人数や継続率の記載がない" },
  { criterionId: "SOLUTION_SCENARIOS", verdict: "YES", score: 5, maxScore: 5,
    evidence: "導入事例が12件、業種と従業員数つきで載っている" },
  { criterionId: "VERIFIABLE_AUTHORITY", verdict: "PARTIAL", score: 2.5, maxScore: 5,
    evidence: "「法改正に対応」とあるが、どの制度にどう対応しているかの説明がない" },
  { criterionId: "FAQ_PRESENCE", verdict: "NO", score: 0, maxScore: 5,
    evidence: "よくある質問のページが見当たらない" },
  { criterionId: "NUMBERED_PROCESS_FLOW", verdict: "YES", score: 5, maxScore: 5,
    evidence: "「導入の流れ」が5つの手順で、番号付きで書かれている" },
  { criterionId: "ENTITY_BIOGRAPHY", verdict: "PARTIAL", score: 2.5, maxScore: 5,
    evidence: "会社概要はあるが、監修している社会保険労務士の名前や経歴の記載がない" },
  { criterionId: "LOCAL_CONTEXT", verdict: "YES", score: 5, maxScore: 5,
    evidence: "日本の残業時間の上限に合わせた設定例と、国内のサポート窓口の受付時間が明記されている" },
  { criterionId: "PRICE_AND_CONSTRAINTS", verdict: "NO", score: 0, maxScore: 5,
    evidence: "料金は「お問い合わせください」だけで、目安や、対応していない機能の記載がない" },
  { criterionId: "EXTERNAL_CITATIONS", verdict: "PARTIAL", score: 2.5, maxScore: 5,
    evidence: "IT製品の比較サイトへの掲載が1件だけ紹介されている" },
];

export const DEMO_TECHNICAL_EVIDENCE =
  "Schema.org: 未実装、見出しの階層: 適切、robots.txt: あり、llms.txt: なし、ページの表示速度: 標準";

/**
 * 内容 25.0 + 構造 12.0 + 権威 10.5 = 47.5。
 * 企業向けは「地域の評判」の枠を持たず、第三者言及が 0〜30 点になる（GeoVisibilityCalculatorService と同じ。素点 7.0 × 1.5）。
 */
export const DEMO_SCORE_BREAKDOWN: ScoreBreakdown = {
  // 旧モデル（後方互換・表示には使わない）
  aiAuditTotal: 25.0,
  meoTotal: 0,
  machineReadabilityTotal: 12.0,
  finalScore: 47.5,
  contentTotal: 25.0,
  technicalTotal: 12.0,
  authorityTotal: 10.5,
  authorityThirdPartyCore: 10.5,
  authorityLocalMeoSub: 0,
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
    title: "トップページの冒頭で、何のサービスかを一文で伝える",
    content: "",
    impactScore: 0.9,
    rationale: "AIは冒頭の文からサービスの特徴を読み取ります。ここがキャッチコピーだけだと、何のサービスとして紹介すればよいか判断できません。",
    evidence: null,
    level: 3,
    requiresProPlan: true,
    isMasked: true,
  },
  {
    id: "demo-task-2",
    category: "SLAB",
    priority: "S",
    title: "導入事例に「導入前に何に困っていたか」を書き足す",
    content: "",
    impactScore: 0.8,
    rationale: "シフト勤務の会社で選ばれていることは、競合との違いになります。困りごとまで書くと、AIが紹介文に使いやすくなります。",
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
    content: "導入にかかる期間・給与計算ソフトとの連携・無料で試せる期間・サポートの範囲など、問い合わせの前によく聞かれることを10問ほど、問いと答えの形で載せます。",
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
    title: "料金ページに、1人あたりの月額の目安を書く",
    content: "「1人あたり月額300円から（初期費用なし）」のように、数字で書きます。対応していない機能も書いておきます。",
    impactScore: 0.5,
    rationale: "料金を聞かれたAIは、数字が書かれたページをもとに答えます。目安があると、比較の回答に取り上げられやすくなります。",
    evidence: "料金は「お問い合わせください」だけで、目安や、対応していない機能の記載がない",
    level: 2,
    requiresProPlan: false,
    isMasked: false,
  },
  {
    id: "demo-task-5",
    category: "SLAB",
    priority: "B",
    title: "IT製品の比較サイトに、掲載を依頼する",
    content: "IT製品の比較サイトや、勤怠管理のサービスを紹介する記事に掲載を依頼し、自社サイトから掲載先を紹介します。",
    impactScore: 0.3,
    rationale: "自社以外のサイトで紹介されていると、AIが信頼できるサービスと判断する材料になります。",
    evidence: "IT製品の比較サイトへの掲載が1件だけ紹介されている",
    level: 1,
    requiresProPlan: false,
    isMasked: false,
  },
];

export const DEMO_ROADMAP: JobRoadmapItem[] = [
  {
    phase: "NOW",
    phaseLabel: "今すぐ",
    title: "トップページで、何のサービスかを伝える",
    rationale: "AIがサービスを読み取れないうちは、ほかの改善が効きません。",
    expectedImpact: "比較や料金の質問でもサービス名が出る下地ができる",
    taskRange: { first: 1, last: 1 },
  },
  {
    phase: "SHORT_TERM",
    phaseLabel: "1〜3ヶ月",
    title: "代表的な導入事例とよくある質問を整える",
    rationale: "競合がAIに選ばれているのは、よくある質問のページです。事例は代表の3件に絞り、負担を抑えます。",
    expectedImpact: "競合に取られている質問で選ばれるようになる",
    taskRange: { first: 2, last: 3 },
  },
  {
    phase: "MID_TERM",
    phaseLabel: "3〜6ヶ月",
    title: "料金の目安と第三者の記事で裏づける",
    rationale: "料金の数字や、ほかのサイトでの紹介があると、AIが比較の回答に取り上げやすくなります。",
    expectedImpact: "料金や比較の質問でも紹介されるようになる",
    taskRange: { first: 4, last: 5 },
  },
];

export const DEMO_MINORITY_REPORTS: JobMinorityReport[] = [
  {
    insight: "導入事例12件すべてに「導入前に何に困っていたか」を書き足してから公開する",
    conflictReason: "3か月以上かかり、その間トップページで何のサービスかが伝わらない状態が続くため。",
    evidence: "比較や料金を聞いた質問8問で、サービス名が一度も出ていない（測定結果）",
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
      "測定の事実です。AIに聞いた10の質問のうち、サービス名が出たのは2問だけでした。出た2問はどちらもシフト勤務に触れた質問で、比較や料金を聞いた8問では一度も出ていません。",
    replyTo: null,
    stance: null,
    evidenceKind: "QUERY_MENTIONS",
    evidenceTaskNumber: null,
    evidenceDetail: "シフト勤務の質問 2問中2問で言及 ／ そのほか 8問中0問",
  },
  {
    round: 1,
    speaker: "INNOVATOR",
    summary:
      "本命は改善タスク2です。導入事例ごとに「導入前に何に困っていたか」を書けば、シフト勤務以外の質問でもAIが紹介しやすくなります。3か月かけて全12件に書き足しましょう。",
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
      "3か月は長すぎます。その間、トップページで何のサービスかすら伝わらない状態が続きます。改善タスク1を今すぐ終えるべきです。12件すべてに書き足すのも、事例の会社への確認を考えると重すぎます。",
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
      "確認しました。サービス名が出なかった8問のうち6問で、AIが紹介していたのはA社かB社でした。サイト診断では、よくある質問のページが見当たりませんでした。",
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
      "その割り振りなら賛成です。ただ、事例に困りごとを書くには、その会社の許可が要ります。許可が取れない場合の書き方も決めておくべきです。",
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
