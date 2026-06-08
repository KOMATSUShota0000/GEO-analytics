package com.geo.analytics.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** LLM が生成した検索クエリ群。GEO 可視性を多角的に測るための複数クエリを保持する。 */
public record GeneratedQueries(@JsonProperty("queries") List<String> queries) {
    public GeneratedQueries {
        queries = queries == null ? List.of() : List.copyOf(queries);
    }
}
