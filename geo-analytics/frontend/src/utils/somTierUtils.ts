export type SomTierKey="invisible"|"challenger"|"competitive"|"marketLeader";

export interface SomTierInfo{
  tierKey:SomTierKey;
  title:string;
  subtitle:string;
  accentClass:string;
  ringClass:string;
  advice:string;
  minInclusive:number;
  maxInclusive:number;
}

const MARKET_LEADER:SomTierInfo={
  tierKey:"marketLeader",
  title:"Market Leader",
  subtitle:"市場リーダー",
  accentClass:"bg-amber-100 text-amber-900 border-amber-300",
  ringClass:"text-amber-500",
  advice:"カテゴリを代表する存在としてAIの応答に深く浸透しています。競合による文脈の奪取を防ぐため、PRTIMESでの定期的発信や専門メディアでの独占インタビューを通じ、AIが『この分野の結論』として貴社を参照し続ける状態を維持(防衛的GEO)してください。",
  minInclusive:31,
  maxInclusive:100,
};

const COMPETITIVE:SomTierInfo={
  tierKey:"competitive",
  title:"Competitive",
  subtitle:"競争力あり",
  accentClass:"bg-indigo-100 text-indigo-800 border-indigo-300",
  ringClass:"text-indigo-500",
  advice:"主要な選択肢として認知されています。さらなる露出拡大には、独自の調査レポートや専門的な解説記事の公開が有効です。国内のビジネスメディアや業界特化型ニュースサイトにおいて、AIが引用しやすい『権威ある一次情報』としてのノードを増やしましょう。",
  minInclusive:16,
  maxInclusive:30,
};

const CHALLENGER:SomTierInfo={
  tierKey:"challenger",
  title:"Challenger",
  subtitle:"チャレンジャー",
  accentClass:"bg-blue-100 text-blue-800 border-blue-300",
  ringClass:"text-blue-500",
  advice:"競合との間に信頼の差(TrustGap)が存在します。価格.comやITreview等の主要比較・レビューサイト、SNS、Googleビジネスプロフィールでの肯定的な言及を増やしてください。AIは『ユーザーの評判』を重要な信頼シグナルとして学習しています。",
  minInclusive:6,
  maxInclusive:15,
};

const INVISIBLE:SomTierInfo={
  tierKey:"invisible",
  title:"Invisible",
  subtitle:"存在なし",
  accentClass:"bg-slate-100 text-slate-800 border-slate-300",
  ringClass:"text-slate-500",
  advice:"AIの学習データにおいてブランドの存在が希薄です。まずはPRTIMESでのプレスリリース配信や、Schema.orgを用いた技術的最適化を急いでください。AIクローラーが貴社の情報を『事実(Fact)』として認識するための物理的な接点作りが最優先事項です。",
  minInclusive:0,
  maxInclusive:5,
};

export function getSomTierInfo(score:number):SomTierInfo{
  const s=Math.max(0,Math.min(100,Number.isFinite(score)?score:0));
  if(s>=MARKET_LEADER.minInclusive){
    return MARKET_LEADER;
  }
  if(s>=COMPETITIVE.minInclusive){
    return COMPETITIVE;
  }
  if(s>=CHALLENGER.minInclusive){
    return CHALLENGER;
  }
  return INVISIBLE;
}

export function somScoreProgressRatio(score:number):number{
  const s=Math.max(0,Math.min(100,Number.isFinite(score)?score:0));
  return s/100;
}