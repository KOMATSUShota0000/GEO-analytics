# ADR-052: 計画クエリ数による論理パディングを配線せず撤去する

## 日付
2026-09-19

## 状況

`.cursorrules` 4節は「常に計画上の分母（N_planned）を引数とし、計算上の論理パディング（小標本防衛）を発動させること」と定める。実際には引数だけが受け渡され、**一度も使われていなかった**（#63）。

```java
public static GbvsResult computeWithPlannedQueries(SomRawMetrics metrics, double lAvgJob, long plannedQueryCount) {
    return GeoVisibilityCalculatorService.computeBatch(List.of(metrics), lAvgJob).getFirst();
    //                                                          ↑ plannedQueryCount を渡していない
}
```

呼び出し側はわざわざ `countQueriesByJobId` で件数を取得して渡しており、受け側が捨てていた。引数だけ残して捨て続けるのは `.cursorrules` 10節「孤立したコードは許容しない」に反する。

## 決定（オーナー確定 2026-09-19）

**配線せず、引数ごと撤去する。**

## 理由

### プラン固定のクエリ数に小標本防衛をかけると、物差しが揃わなくなる

クエリ数はプランで固定されている（Standard 3 / Pro 10 / Expert 30）。小標本防衛を配線すると、**同じブランドでも Standard だけが恒久的に低く出る**。これは ADR-039 の決定3「スコアの物差しはプラン共通とし、上位プランで売るのは実測という証拠」と矛盾する。顧客がプランを上げた前後でスコアを比較できなくなる問題も起きる。

### 不確実性はスコアを下げる形ではなく、幅で示す

`.cursorrules` 11節は小標本（N < 30）での「クレディブル区間の可視化」も定めている。**少ないクエリ数の不確実性は、点を下げるのではなく幅で見せるのが筋**。こちらは将来の課題として残す。

## 結果

- `SomScoreCalculator.computeWithPlannedQueries` を撤去し、`computeBatchForJob` と `InformationTheoryBasedAggregator.finalizeGbvsBatchForJob` から計画クエリ数の引数を外した。
- 呼び出し側の `countQueriesByJobId` の呼び出しも撤去した（他の用途では引き続き使われている）。
- スコアの値は変わらない（元から使われていなかったため）。
- `.cursorrules` 4節との整合は本 ADR をもって説明とする。**配線しないことを選んだ理由は「プラン間の比較可能性を優先したため」**であり、小標本の扱いを放置したわけではない。
