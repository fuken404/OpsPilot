package dev.fuken404.opspilot.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AudienceValidatorTests {

    private Jwt token(List<String> audience) {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject("s")
            .audience(audience).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    @Test
    void acceptsTokensForThisApi() {
        assertThat(SecurityConfig.audience("opspilot-api").validate(token(List.of("account", "opspilot-api"))).hasErrors()).isFalse();
    }

    @Test
    void rejectsTokensForOtherApps() {
        assertThat(SecurityConfig.audience("opspilot-api").validate(token(List.of("account"))).hasErrors()).isTrue();
    }
}
