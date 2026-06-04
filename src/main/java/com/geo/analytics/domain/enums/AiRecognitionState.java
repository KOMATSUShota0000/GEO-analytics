package com.geo.analytics.domain.enums;

/**
 * 生成AIが解析対象ブランドを「実体としてどう認識しているか」を表す定性ステート（V13_GEO4AXIS / Sprint3）。
 *
 * <p>SoM測定で<strong>既に取得済みの</strong>LLM応答を再利用して判定する（追加API呼び出しゼロ）。
 * これは<strong>スコアには一切加算しない</strong>。SoM が「どれだけ言及されたか（量）」を測るのに対し、
 * 本ステートは「正しい実体として認識されているか（質）」を表し、レポート上の定性エビデンスとして
 * 改善ストーリーを補強するために用いる。スコアへ混ぜると SoM との二重計上になるため厳禁。
 */
public enum AiRecognitionState {

    /** AIが当該ブランドを正しい実体として認識・言及している。 */
    RECOGNIZED_CORRECTLY,

    /** AIはブランド名に言及したが、別実体（同名他社・ハルシネーション等）と取り違えている。 */
    MISIDENTIFIED,

    /** AIがそもそも当該ブランドを認識していない（言及なし・実体を解決できない）。 */
    UNKNOWN
}
