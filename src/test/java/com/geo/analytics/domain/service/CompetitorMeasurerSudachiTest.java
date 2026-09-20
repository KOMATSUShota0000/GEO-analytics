package com.geo.analytics.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.worksap.nlp.sudachi.Config;
import com.worksap.nlp.sudachi.Dictionary;
import com.worksap.nlp.sudachi.DictionaryFactory;
import com.worksap.nlp.sudachi.PathAnchor;
import java.io.File;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;

/** #112: 競合の実測を1箇所に集約した測定器。名前は LLM、選別と計数は Java。 */
class CompetitorMeasurerSudachiTest {

    private static Dictionary dictionary;
    private static CompetitorMeasurer measurer;

    @BeforeAll
    static void loadDictionary() throws Exception {
        File dictFile = ResourceUtils.getFile("classpath:system_core.dic");
        String settings = "{\"systemDict\":\"" + dictFile.getAbsolutePath().replace("\\", "/") + "\"}";
        Config passed = Config.fromJsonString(settings, PathAnchor.classpath().andThen(PathAnchor.none()));
        dictionary = new DictionaryFactory().create(passed.withFallback(Config.defaultConfig()));
        var nlp = new JapaneseNlpService(dictionary);
        measurer = new CompetitorMeasurer(new BrandMentionEngine(nlp), new EntityNormalizer(nlp));
    }

    @AfterAll
    static void closeDictionary() throws Exception {
        if (dictionary != null) {
            dictionary.close();
        }
    }

    @Test
    void 回答文から競合の言及回数と登場順を実測する() {
        String answer = "クラウド会計ソフトではfreee会計が広く使われています。次にマネーフォワードも人気で、"
                + "自社会計は後発です。freee会計は特に個人事業主に強いです。";

        var results = measurer.measure(answer, "自社会計", List.of("freee会計", "マネーフォワード"), true, 80.0);

        assertThat(results).hasSize(2);
        assertThat(results.getFirst().competitorLabel()).isEqualTo("freee会計");
        assertThat(results.getFirst().mentionCount()).isEqualTo(2);
        assertThat(results.getFirst().aiCitationPosition()).isEqualTo(1);
        assertThat(results.getFirst().somScore()).isNotNull();
        assertThat(results.get(1).competitorLabel()).isEqualTo("マネーフォワード");
        assertThat(results.get(1).aiCitationPosition()).isEqualTo(2);
    }

    @Test
    void 自社と残余カテゴリは競合に混ぜない() {
        String answer = "自社会計とその他のツールが比較されます。";

        var results = measurer.measure(answer, "自社会計", List.of("自社会計", "その他"), false, 40.0);

        assertThat(results).isEmpty();
    }

    @Test
    void 競合名が挙がらなければ空を返す() {
        assertThat(measurer.measure("本文", "自社会計", List.of(), true, 10.0)).isEmpty();
        assertThat(measurer.measure("本文", "自社会計", null, true, 10.0)).isEmpty();
    }
}
