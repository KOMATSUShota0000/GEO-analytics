package com.geo.analytics.domain.service;

import com.geo.analytics.domain.model.ResolvableEntity;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public final class EntityResolutionBlockingService {
    private static final HexFormat HEX = HexFormat.of();
    private final JapaneseNlpService japaneseNlpService;

    public EntityResolutionBlockingService(JapaneseNlpService japaneseNlpService) {
        this.japaneseNlpService = japaneseNlpService;
    }

    public String blockingConcatKey(String surface) {
        var prepared = EntityNormalizer.prepareForSudachi(surface);
        if (prepared.isBlank()) {
            return "_";
        }
        var nf = japaneseNlpService.normalizedForm(prepared);
        var rf = japaneseNlpService.readingForm(prepared);
        return nf + "_" + rf;
    }

    public String blockingHashSha256(String surface) {
        var concat = blockingConcatKey(surface);
        try {
            var md = MessageDigest.getInstance("SHA-256");
            var digest = md.digest(concat.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public Map<String, List<ResolvableEntity>> bucketByBlockingHash(List<ResolvableEntity> entities) {
        return entities.stream()
            .collect(Collectors.groupingBy(e -> blockingHashSha256(e.label())));
    }
}
