package dev.fuken404.opspilot.sla;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("opspilot.sla")
record SlaProperties(String zone, String opens, String closes, boolean schedulerEnabled,
                     Duration sweepInterval) {
}
