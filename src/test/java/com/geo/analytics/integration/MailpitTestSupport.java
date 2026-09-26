package com.geo.analytics.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * 結合テスト用の使い捨て Mailpit。アプリが本物の SMTP で送ったメールを受け止め、API で読み出す（#144 確定事項15）。
 * 開発用の scripts/mail.sh と同じ版を使う。
 */
public final class MailpitTestSupport {

    private static final int SMTP_PORT = 1025;
    private static final int API_PORT = 8025;
    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final GenericContainer<?> MAILPIT = createContainer();

    public record Message(String id, String to, String subject, String text) {}

    private MailpitTestSupport() {}

    @SuppressWarnings("resource")
    private static GenericContainer<?> createContainer() {
        var c = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.31.2"))
                .withExposedPorts(SMTP_PORT, API_PORT)
                .waitingFor(Wait.forHttp("/readyz").forPort(API_PORT));
        if (DockerClientFactory.instance().isDockerAvailable()) {
            c.start();
        }
        return c;
    }

    public static void registerMailProperties(DynamicPropertyRegistry registry) {
        if (!MAILPIT.isRunning()) {
            throw new IllegalStateException("Docker is required for Mailpit-backed tests.");
        }
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(SMTP_PORT));
    }

    public static void deleteAll() {
        send(HttpRequest.newBuilder(api("/api/v1/messages")).DELETE().build());
    }

    public static List<Message> messagesTo(String address) {
        JsonNode list = json(send(HttpRequest.newBuilder(api("/api/v1/messages")).GET().build()))
                .path("messages");
        List<Message> result = new ArrayList<>();
        for (JsonNode summary : list) {
            for (JsonNode to : summary.path("To")) {
                if (address.equalsIgnoreCase(to.path("Address").asText())) {
                    result.add(read(summary.path("ID").asText()));
                }
            }
        }
        return result;
    }

    public static int totalMessages() {
        return json(send(HttpRequest.newBuilder(api("/api/v1/messages")).GET().build()))
                .path("messages_count")
                .asInt();
    }

    /** 送信は別スレッドで行われるため、届くまで待つ。 */
    public static List<Message> awaitMessagesTo(String address, int count, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (true) {
            List<Message> messages = messagesTo(address);
            if (messages.size() >= count) {
                return messages;
            }
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError(
                        address + " 宛てのメールが " + timeout + " 以内に " + count + " 通届きませんでした（届いたのは "
                                + messages.size() + " 通）");
            }
            try {
                Thread.sleep(POLL_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted", e);
            }
        }
    }

    private static Message read(String id) {
        JsonNode message = json(send(HttpRequest.newBuilder(api("/api/v1/message/" + id)).GET().build()));
        return new Message(
                id,
                message.path("To").path(0).path("Address").asText(),
                message.path("Subject").asText(),
                message.path("Text").asText());
    }

    private static JsonNode json(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static URI api(String path) {
        return URI.create("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(API_PORT) + path);
    }

    private static String send(HttpRequest request) {
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Mailpit API " + request.uri() + " -> " + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
