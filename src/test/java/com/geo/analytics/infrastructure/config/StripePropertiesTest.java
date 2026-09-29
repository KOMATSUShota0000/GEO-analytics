package com.geo.analytics.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class StripePropertiesTest {

    @Test
    void blankDotenvLines_keepDefaultReturnUrls_andLeavePricesUnset() throws IOException {
        Map<String, Object> dotenv = new HashMap<>();
        dotenv.put("STRIPE_SECRET_KEY", "");
        dotenv.put("STRIPE_WEBHOOK_SECRET", "");
        dotenv.put("STRIPE_PRICE_STANDARD", "");
        dotenv.put("STRIPE_PRICE_PRO", "");
        dotenv.put("STRIPE_PRICE_EXPERT", "");
        dotenv.put("STRIPE_SUCCESS_URL", "");
        dotenv.put("STRIPE_CANCEL_URL", "");

        StripeProperties properties = bindFromApplicationYaml(dotenv);

        assertThat(properties.getSuccessUrl()).isEqualTo(StripeProperties.DEFAULT_SUCCESS_URL);
        assertThat(properties.getCancelUrl()).isEqualTo(StripeProperties.DEFAULT_CANCEL_URL);
        assertThat(properties.getSecretKey()).isEmpty();
        assertThat(properties.getPrices().getStandard()).isEmpty();
        assertThat(properties.getPrices().getPro()).isEmpty();
        assertThat(properties.getPrices().getExpert()).isEmpty();
    }

    @Test
    void absentDotenvLines_leavePricesUnset_insteadOfFallingBackToSomeoneElsesAccount() throws IOException {
        StripeProperties properties = bindFromApplicationYaml(Map.of());

        assertThat(properties.getPrices().getStandard()).isEmpty();
        assertThat(properties.getPrices().getPro()).isEmpty();
        assertThat(properties.getPrices().getExpert()).isEmpty();
        assertThat(properties.getSuccessUrl()).isEqualTo(StripeProperties.DEFAULT_SUCCESS_URL);
        assertThat(properties.getCancelUrl()).isEqualTo(StripeProperties.DEFAULT_CANCEL_URL);
    }

    @Test
    void explicitValues_areUsedAsIs() throws IOException {
        StripeProperties properties = bindFromApplicationYaml(Map.of(
                "STRIPE_SECRET_KEY", "sk_test_dummy",
                "STRIPE_PRICE_PRO", "price_pro_dummy",
                "STRIPE_SUCCESS_URL", "https://app.example.com/?billing=success",
                "STRIPE_CANCEL_URL", "https://app.example.com/pricing?billing=cancel"));

        assertThat(properties.getSecretKey()).isEqualTo("sk_test_dummy");
        assertThat(properties.getPrices().getPro()).isEqualTo("price_pro_dummy");
        assertThat(properties.getSuccessUrl()).isEqualTo("https://app.example.com/?billing=success");
        assertThat(properties.getCancelUrl()).isEqualTo("https://app.example.com/pricing?billing=cancel");
    }

    @Test
    void cancelUrl_returnsToTheLoggedInPricingPage() {
        assertThat(new StripeProperties().getCancelUrl()).contains("/pricing");
    }

    // Why: 実際の不具合は「.env の空行」と「application.yml のプレースホルダ」の組み合わせで起きたため、
    //      セッター単体ではなく本物の application.yml を通して束縛する。システム環境変数は混ざらないよう外す。
    private static StripeProperties bindFromApplicationYaml(Map<String, Object> dotenv) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("dotenv", dotenv));
        for (PropertySource<?> yaml : new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addLast(yaml);
        }
        return Binder.get(environment).bindOrCreate("stripe", StripeProperties.class);
    }
}
