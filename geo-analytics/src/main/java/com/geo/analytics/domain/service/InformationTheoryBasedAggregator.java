package com.geo.analytics.domain.service;

import com.geo.analytics.application.dto.CompetitorResult;
import com.geo.analytics.application.dto.VerificationRequest;
import com.geo.analytics.application.dto.VerificationResponse;
import com.geo.analytics.domain.enums.MatchStatus;
import com.geo.analytics.domain.enums.ModelType;
import com.geo.analytics.domain.exception.AiAnalysisTimeoutException;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

@Component
public class InformationTheoryBasedAggregator {
    public static final String AGGREGATION_CALCULATION_VERSION = "SUBSCRIPTION_INTEGRATION_V4.13";
    private static final double P_WINKLER = 0.1;
    private static final int WINKLER_PREFIX_MAX = 4;
    private static final Pattern LEGAL_ENTITY = Pattern.compile(
            "^(株式会社|有限会社|合同会社|一般社団法人|財団法人)\\s*(.+)$|(.+?)\\s*(株式会社|有限会社|合同会社|一般社団法人|財団法人)$");
    private final ReentrantLock indexLock = new ReentrantLock();

    public VerificationResponse aggregate(List<VerificationResponse> successes, VerificationRequest request) {
        if (successes == null || successes.isEmpty()) {
            throw new AiAnalysisTimeoutException();
        }
        var n = successes.size();
        var insights = new LinkedHashMap<ModelType, String>();
        for (var v : successes) {
            insights.put(v.modelType(), v.rawResponseJson());
        }
        SequencedMap<ModelType, String> insightView = Collections.unmodifiableSequencedMap(insights);
        double sumSom = 0.0;
        for (var v : successes) {
            sumSom += v.somScore() != null ? v.somScore() : 0.0;
        }
        double finalSom = Math.clamp(sumSom / n, 0.0, 100.0);
        finalSom = Math.round(finalSom * 100.0) / 100.0;
        var brand = successes.stream().anyMatch(v -> Boolean.TRUE.equals(v.brandMentioned()));
        var sumMr = 0;
        var sumRp = 0;
        var sumTok = 0;
        var sumOv = 0;
        var sumVs = 0;
        var countVs = 0;
        var sumSi = 0.0;
        var sumMz = 0.0;
        var countMz = 0;
        for (var v : successes) {
            if (v.mentionRank() != null) {
                sumMr += v.mentionRank();
            }
            sumRp += v.rankPosition();
            sumTok += v.tokenCount();
            if (v.overallScore() != null) {
                sumOv += v.overallScore();
            }
            sumSi += v.sentimentIntensity();
            if (v.visibilityStage() != null) {
                sumVs += v.visibilityStage();
                countVs++;
            }
            if (v.modifiedZScore() != null) {
                sumMz += v.modifiedZScore();
                countMz++;
            }
        }
        var avgMr = sumMr / n;
        var avgRp = sumRp / n;
        var avgTok = sumTok / n;
        var avgOv = sumOv / n;
        var avgSi = sumSi / n;
        var first = successes.getFirst();
        Integer avgVs = countVs > 0 ? sumVs / countVs : first.visibilityStage();
        Double avgMz = countMz > 0 ? sumMz / countMz : first.modifiedZScore();
        var inverted = new ConcurrentHashMap<String, Set<String>>();
        var registered = request.registeredCompetitorBrands() == null
                ? List.<String>of()
                : request.registeredCompetitorBrands().stream().limit(3).toList();
        for (var reg : registered) {
            var key = pipeline(reg);
            for (var bg : bigrams(key)) {
                indexLock.lock();
                try {
                    inverted.computeIfAbsent(bg, b -> ConcurrentHashMap.newKeySet()).add(key);
                } finally {
                    indexLock.unlock();
                }
            }
        }
        var merged = new ArrayList<CompetitorResult>();
        for (var regDisplay : registered) {
            var regNorm = pipeline(regDisplay);
            var matchedSoms = new ArrayList<Double>();
            var matchedRanks = new ArrayList<Integer>();
            var matchedVs = new ArrayList<Integer>();
            var statusBands = new ArrayList<MatchStatus>();
            for (var resp : successes) {
                var pick = pickCompetitorForRegistered(resp, regNorm, inverted);
                if (pick == null) {
                    continue;
                }
                var jw = jaroWinkler(regNorm, pipeline(pick.competitorLabel()));
                if (jw < 0.80) {
                    continue;
                }
                var ms = jw >= 0.92 ? MatchStatus.AUTO_MATCH : MatchStatus.MANUAL_REVIEW;
                statusBands.add(ms);
                matchedSoms.add(pick.somScore() != null ? pick.somScore() : 0.0);
                matchedRanks.add(pick.rankPosition() != null ? pick.rankPosition() : 0);
                matchedVs.add(pick.visibilityStage() != null ? pick.visibilityStage() : 0);
            }
            if (matchedSoms.isEmpty()) {
                merged.add(new CompetitorResult(regDisplay, 0.0, 0, 0, MatchStatus.NO_MATCH));
            } else {
                var avgC = matchedSoms.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                avgC = Math.round(avgC * 100.0) / 100.0;
                avgC = Math.clamp(avgC, 0.0, 100.0);
                var aggRank = (int) Math.round(matchedRanks.stream().mapToInt(Integer::intValue).average().orElse(0.0));
                var aggVs = (int) Math.round(matchedVs.stream().mapToInt(Integer::intValue).average().orElse(0.0));
                var aggMs = statusBands.contains(MatchStatus.MANUAL_REVIEW)
                        ? MatchStatus.MANUAL_REVIEW
                        : MatchStatus.AUTO_MATCH;
                merged.add(new CompetitorResult(regDisplay, avgC, aggRank, aggVs, aggMs));
            }
        }
        return new VerificationResponse(
                first.modelType(),
                first.rawResponseJson(),
                finalSom,
                brand,
                avgMr,
                avgOv,
                avgTok,
                avgRp,
                avgSi,
                first.resolvedEntityLabel(),
                avgVs,
                avgMz,
                AGGREGATION_CALCULATION_VERSION,
                merged,
                insightView);
    }

    private CompetitorResult pickCompetitorForRegistered(
            VerificationResponse resp,
            String regNorm,
            ConcurrentHashMap<String, Set<String>> inverted) {
        CompetitorResult best = null;
        var bestJw = -1.0;
        for (var cr : resp.competitorResults()) {
            var aiNorm = pipeline(cr.competitorLabel());
            if (inverted.isEmpty()) {
                var jw = jaroWinkler(regNorm, aiNorm);
                if (jw > bestJw) {
                    bestJw = jw;
                    best = cr;
                }
                continue;
            }
            var cand = blockingCandidates(aiNorm, inverted);
            if (cand.isEmpty() || !cand.contains(regNorm)) {
                continue;
            }
            var jw = jaroWinkler(regNorm, aiNorm);
            if (jw > bestJw) {
                bestJw = jw;
                best = cr;
            }
        }
        return best;
    }

    private Set<String> blockingCandidates(String normalizedAi, ConcurrentHashMap<String, Set<String>> inverted) {
        var votes = new HashMap<String, Integer>();
        for (var bg : bigrams(normalizedAi)) {
            var bucket = inverted.get(bg);
            if (bucket == null) {
                continue;
            }
            for (var regKey : bucket) {
                votes.merge(regKey, 1, Integer::sum);
            }
        }
        var out = new HashSet<String>();
        for (var e : votes.entrySet()) {
            if (e.getValue() >= 2) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    public String pipeline(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        var n = Normalizer.normalize(input.trim(), Normalizer.Form.NFKC);
        n = n.toUpperCase(Locale.ROOT);
        n = n.replaceAll("[\\p{Punct}\\s]+", "");
        var m = LEGAL_ENTITY.matcher(n);
        if (m.matches() && m.group(1) != null) {
            var corp = m.group(1);
            var rest = m.group(2);
            if (rest != null) {
                return rest + corp;
            }
        }
        return n;
    }

    private List<String> bigrams(String s) {
        if (s == null || s.length() < 2) {
            return List.of();
        }
        var cps = s.codePoints().toArray();
        if (cps.length < 2) {
            return List.of();
        }
        var out = new ArrayList<String>();
        for (var i = 0; i < cps.length - 1; i++) {
            out.add(new String(new int[]{cps[i], cps[i + 1]}, 0, 2));
        }
        return out;
    }

    private double jaroWinkler(String s1, String s2) {
        var j = jaro(s1, s2);
        var prefix = 0;
        var maxP = Math.min(WINKLER_PREFIX_MAX, Math.min(s1.length(), s2.length()));
        for (var i = 0; i < maxP; i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                prefix++;
            } else {
                break;
            }
        }
        return j + prefix * P_WINKLER * (1.0 - j);
    }

    private static double jaro(String s1, String s2) {
        if (s1.isEmpty() && s2.isEmpty()) {
            return 1.0;
        }
        if (s1.isEmpty() || s2.isEmpty()) {
            return 0.0;
        }
        var m = Math.max(s1.length(), s2.length()) / 2 - 1;
        if (m < 0) {
            m = 0;
        }
        var s1Matches = new boolean[s1.length()];
        var s2Matches = new boolean[s2.length()];
        var matches = 0;
        for (var i = 0; i < s1.length(); i++) {
            var start = Math.max(0, i - m);
            var end = Math.min(i + m + 1, s2.length());
            for (var j = start; j < end; j++) {
                if (s2Matches[j] || s1.charAt(i) != s2.charAt(j)) {
                    continue;
                }
                s1Matches[i] = true;
                s2Matches[j] = true;
                matches++;
                break;
            }
        }
        if (matches == 0) {
            return 0.0;
        }
        var t = 0;
        var k = 0;
        for (var i = 0; i < s1.length(); i++) {
            if (!s1Matches[i]) {
                continue;
            }
            while (k < s2.length() && !s2Matches[k]) {
                k++;
            }
            if (k < s2.length() && s1.charAt(i) != s2.charAt(k)) {
                t++;
            }
            k++;
        }
        t /= 2;
        return ((matches / (double) s1.length())
                + (matches / (double) s2.length())
                + (matches - t) / matches)
                / 3.0;
    }
}
