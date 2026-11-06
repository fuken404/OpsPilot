package dev.fuken404.opspilot.shared;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("opspilot.security")
public record SecurityProperties(String audience, List<String> allowedOrigins) {
}
