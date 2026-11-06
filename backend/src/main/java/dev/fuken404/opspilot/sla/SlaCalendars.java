package dev.fuken404.opspilot.sla;

import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(SlaProperties.class)
class SlaCalendars {

    private final BusinessCalendar business;

    SlaCalendars(SlaProperties properties) {
        this.business = new BusinessCalendar(ZoneId.of(properties.zone()), LocalTime.parse(properties.opens()),
            LocalTime.parse(properties.closes()));
    }

    WorkingTime of(boolean businessHours) {
        return businessHours ? business : WorkingTime.ALWAYS;
    }
}
