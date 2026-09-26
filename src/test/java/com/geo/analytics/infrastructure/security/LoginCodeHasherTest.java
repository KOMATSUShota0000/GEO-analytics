package com.geo.analytics.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.geo.analytics.infrastructure.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LoginCodeHasherTest {

    private static final UUID USER_A = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa01");
    private static final UUID USER_B = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa02");

    private static LoginCodeHasher hasherWithSecret(String secret) {
        AppProperties props = new AppProperties();
        AppProperties.Security security = new AppProperties.Security();
        AppProperties.Jwt jwt = new AppProperties.Jwt();
        jwt.setSecret(secret);
        security.setJwt(jwt);
        props.setSecurity(security);
        return new LoginCodeHasher(props);
    }

    private final LoginCodeHasher hasher = hasherWithSecret("0123456789abcdef0123456789abcdef-test-secret");

    @Test
    void sameInput_givesSameHexHash() {
        String hash = hasher.hash(USER_A, "123456");

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}").isEqualTo(hasher.hash(USER_A, "123456"));
    }

    @Test
    void differentCodeOrUser_givesDifferentHash() {
        String base = hasher.hash(USER_A, "123456");

        assertThat(hasher.hash(USER_A, "123457")).isNotEqualTo(base);
        assertThat(hasher.hash(USER_B, "123456")).isNotEqualTo(base);
    }

    @Test
    void hashCannotBeReproducedWithoutTheServerSecret() throws Exception {
        String plainSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((USER_A + ":123456").getBytes(StandardCharsets.UTF_8)));

        assertThat(hasher.hash(USER_A, "123456")).isNotEqualTo(plainSha256);
        assertThat(hasherWithSecret("another-secret-another-secret-another").hash(USER_A, "123456"))
                .isNotEqualTo(hasher.hash(USER_A, "123456"));
    }

    @Test
    void missingSecret_stopsStartup() {
        assertThatThrownBy(() -> hasherWithSecret(" ")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new LoginCodeHasher(new AppProperties())).isInstanceOf(IllegalStateException.class);
    }
}
