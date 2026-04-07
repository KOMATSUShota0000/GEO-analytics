package com.geo.analytics.infrastructure.config;

import com.geo.analytics.domain.matching.NormalizationLayer;
import com.geo.analytics.domain.matching.TokenizerManager;
import com.geo.analytics.domain.service.GeoVisibilityCalculatorService;
import com.geo.analytics.domain.service.JapaneseNlpService;
import com.worksap.nlp.sudachi.Dictionary;
import com.worksap.nlp.sudachi.DictionaryFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ResourceUtils;
import java.io.File;
import java.io.IOException;

@Configuration
public class SudachiConfig {
    @Bean(destroyMethod = "close")
    public Dictionary sudachiDictionary() throws IOException {
        File dictFile = ResourceUtils.getFile("classpath:system_core.dic");
        String dictPath = dictFile.getAbsolutePath().replace("\\", "/");
        String settings = "{\"systemDict\":\"" + dictPath + "\"}";
        return new DictionaryFactory().create(settings);
    }

    @Bean
    public JapaneseNlpService japaneseNlpService(Dictionary sudachiDictionary) {
        return new JapaneseNlpService(sudachiDictionary);
    }

    @Bean
    public TokenizerManager tokenizerManager(Dictionary sudachiDictionary) {
        return new TokenizerManager(sudachiDictionary);
    }

    @Bean
    public NormalizationLayer normalizationLayer(TokenizerManager tokenizerManager) {
        return new NormalizationLayer(tokenizerManager);
    }

    @Bean
    public GeoVisibilityCalculatorService geoVisibilityCalculatorService(TokenizerManager tokenizerManager) {
        return new GeoVisibilityCalculatorService(tokenizerManager);
    }
}
