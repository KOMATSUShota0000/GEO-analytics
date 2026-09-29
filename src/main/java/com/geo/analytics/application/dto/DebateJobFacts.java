package com.geo.analytics.application.dto;

/**
 * 4ペルソナ議論に渡す、ジョブ単位の前提（解析の依頼時に入力された事業情報と、サイト診断の結果）。
 *
 * <p>Why: プロジェクト側の業種・強み・ターゲットは、入口の無いオンボーディング画面でしか書き込めず実際には空になる（#191）。
 * ジョブには依頼時の入力と診断結果が必ず残るため、議論の材料はこちらから取る（#195）。
 */
public record DebateJobFacts(
        String businessSummary, String targetAudience, String focusPoints, String selfRubricAuditJson) {

    public static DebateJobFacts empty() {
        return new DebateJobFacts(null, null, null, null);
    }
}
