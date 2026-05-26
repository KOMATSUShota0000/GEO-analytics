package com.geo.analytics.application.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

@Service
public class KnowledgeFileParsingService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeFileParsingService.class);
    private static final int MAX_COMBINED_CHARS = 20000;
    private static final String PART_SEPARATOR = "\n----\n";

    private final ApacheTikaDocumentParser documentParser = new ApacheTikaDocumentParser();

    public String parseAndConcatenate(List<Path> filePaths) {
        if (filePaths == null || filePaths.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(Math.min(MAX_COMBINED_CHARS + PART_SEPARATOR.length(), 20096));
        for (Path path : filePaths) {
            if (path == null) {
                continue;
            }
            if (out.length() >= MAX_COMBINED_CHARS) {
                break;
            }
            String fragment;
            try (InputStream in = Files.newInputStream(path)) {
                Document doc = documentParser.parse(in);
                fragment = doc.text();
            } catch (Exception e) {
                log.warn("Knowledge file parse failed path={}", path.toAbsolutePath(), e);
                continue;
            }
            fragment = Objects.requireNonNullElse(fragment, "");
            if (fragment.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                int room = MAX_COMBINED_CHARS - out.length();
                if (room <= 0) {
                    break;
                }
                if (room < PART_SEPARATOR.length()) {
                    break;
                }
                out.append(PART_SEPARATOR);
            }
            int room = MAX_COMBINED_CHARS - out.length();
            if (room <= 0) {
                break;
            }
            int take = Math.min(fragment.length(), room);
            out.append(fragment, 0, take);
        }
        if (out.isEmpty()) {
            return "";
        }
        return out.toString();
    }
}
