package com.geo.analytics.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

@Component
public class PdfStorageConfig {
    private static final Logger log = LoggerFactory.getLogger(PdfStorageConfig.class);

    private final Path tempDirectory;

    public PdfStorageConfig(AppProperties appProperties) throws IOException {
        String tempDir = appProperties.getPdf().getTempDir();
        if (tempDir == null || tempDir.isBlank()) {
            throw new IllegalStateException("app.pdf.temp-dir must be configured");
        }
        this.tempDirectory = Path.of(tempDir).toAbsolutePath().normalize();
        Files.createDirectories(this.tempDirectory);
    }

    public Path getTempDirectory() {
        return tempDirectory;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void deleteAllTempFilesOnStartup() {
        try (Stream<Path> entries = Files.list(tempDirectory)) {
            entries.filter(Files::isRegularFile).forEach(this::deleteQuietly);
        } catch (IOException ioException) {
            log.warn("pdf temp startup cleanup failed: {}", ioException.getMessage());
        }
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void deletePdfFilesOlderThanTwentyFourHours() {
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        try (Stream<Path> entries = Files.list(tempDirectory)) {
            entries.filter(Files::isRegularFile).forEach(path -> {
                try {
                    if (Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)) {
                        deleteQuietly(path);
                    }
                } catch (IOException ioException) {
                    log.warn("pdf temp age check failed {}: {}", path, ioException.getMessage());
                }
            });
        } catch (IOException ioException) {
            log.warn("pdf temp scheduled cleanup failed: {}", ioException.getMessage());
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ioException) {
            log.warn("pdf temp delete failed {}: {}", path, ioException.getMessage());
        }
    }
}
