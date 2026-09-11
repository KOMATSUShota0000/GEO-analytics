package com.geo.analytics.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.worksap.nlp.sudachi.Config;
import com.worksap.nlp.sudachi.Dictionary;
import com.worksap.nlp.sudachi.DictionaryFactory;
import com.worksap.nlp.sudachi.PathAnchor;
import java.io.File;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;

/**
 * #59: 実際の Sudachi 辞書で、形態素境界と正規化が本文の添字と揃っていることを確かめる。
 * 辞書の読み込み手順は SudachiConfig と同じ。
 */
class BrandMentionEngineSudachiTest {

    private static Dictionary dictionary;
    private static BrandMentionEngine engine;

    @BeforeAll
    static void loadDictionary() throws Exception {
        File dictFile = ResourceUtils.getFile("classpath:system_core.dic");
        String settings = "{\"systemDict\":\"" + dictFile.getAbsolutePath().replace("\\", "/") + "\"}";
        Config passed = Config.fromJsonString(settings, PathAnchor.classpath().andThen(PathAnchor.none()));
        dictionary = new DictionaryFactory().create(passed.withFallback(Config.defaultConfig()));
        engine = new BrandMentionEngine(new JapaneseNlpService(dictionary));
    }

    @AfterAll
    static void closeDictionary() throws Exception {
        if (dictionary != null) {
            dictionary.close();
        }
    }

    @Test
    void 漢字の語に続く英字ブランドも境界で数える() {
        var m = engine.measure("freee会計はfreeeの主力製品です。", "freee");

        assertThat(m.mentionCount()).isEqualTo(2);
        assertThat(m.totalTokens()).isPositive();
    }

    @Test
    void 全角と大文字小文字の揺れを吸収する() {
        var m = engine.measure("ＦＲＥＥＥは便利です。Freeeの自動仕訳も評判です。", "freee");

        assertThat(m.mentionCount()).isEqualTo(2);
    }

    @Test
    void カタカナの語の途中に誤一致しない() {
        var m = engine.measure("ガストロノミーの店とガストを比べる。", "ガスト");

        assertThat(m.mentionCount()).isEqualTo(1);
    }

    @Test
    void 言及が無ければ0件() {
        var m = engine.measure("クラウド会計ソフトは複数あります。", "freee");

        assertThat(m.mentionCount()).isZero();
        assertThat(m.density()).isZero();
    }
}
