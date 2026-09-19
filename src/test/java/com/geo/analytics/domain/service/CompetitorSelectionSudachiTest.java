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

/** #64 / #65: 競合の選別。自社・残余カテゴリ・表記ゆれの重複を落とす。辞書の読み込みは SudachiConfig と同じ。 */
class CompetitorSelectionSudachiTest {

    private static Dictionary dictionary;
    private static EntityNormalizer normalizer;

    @BeforeAll
    static void loadDictionary() throws Exception {
        File dictFile = ResourceUtils.getFile("classpath:system_core.dic");
        String settings = "{\"systemDict\":\"" + dictFile.getAbsolutePath().replace("\\", "/") + "\"}";
        Config passed = Config.fromJsonString(settings, PathAnchor.classpath().andThen(PathAnchor.none()));
        dictionary = new DictionaryFactory().create(passed.withFallback(Config.defaultConfig()));
        normalizer = new EntityNormalizer(new JapaneseNlpService(dictionary));
    }

    @AfterAll
    static void closeDictionary() throws Exception {
        if (dictionary != null) {
            dictionary.close();
        }
    }

    @Test
    void 自社は競合から外す() {
        var accepted = CompetitorSelection.accept(
                List.of("freee会計", "マネーフォワード クラウド会計"), "freee会計", normalizer);

        assertThat(accepted).containsExactly("マネーフォワード クラウド会計");
    }

    @Test
    void その他などの残余カテゴリは外す() {
        var accepted = CompetitorSelection.accept(
                List.of("弥生会計", "その他", "Others", "他"), "freee", normalizer);

        assertThat(accepted).containsExactly("弥生会計");
    }

    @Test
    void 同じ表記は一度だけ採る() {
        var accepted = CompetitorSelection.accept(
                List.of("弥生会計", "弥生会計", "マネーフォワード"), "freee", normalizer);

        assertThat(accepted).containsExactly("弥生会計", "マネーフォワード");
    }

    @Test
    void 空や空白の名前は無視する() {
        var accepted = CompetitorSelection.accept(
                java.util.Arrays.asList("", "   ", null, "弥生会計"), "freee", normalizer);

        assertThat(accepted).containsExactly("弥生会計");
    }

    @Test
    void LLMが挙げた順序を保つ() {
        var accepted = CompetitorSelection.accept(
                List.of("弥生会計", "マネーフォワード", "invoy"), "freee", normalizer);

        assertThat(accepted).containsExactly("弥生会計", "マネーフォワード", "invoy");
    }
}
