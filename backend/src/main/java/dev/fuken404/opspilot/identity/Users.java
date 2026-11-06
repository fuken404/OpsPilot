package dev.fuken404.opspilot.identity;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Users {

    private final AppUserRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    Users(AppUserRepository repository, ApplicationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Devuelve el usuario del token y lo crea la primera vez. Si dos peticiones del mismo usuario
     * llegan juntas, la base decide cuál lo crea y ambas reciben el mismo registro.
     */
    @Transactional
    public AppUser current(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        String name = Optional.ofNullable(jwt.getClaimAsString("name")).orElse(email);
        Optional<AppUser> existing = repository.findBySubject(jwt.getSubject());
        if (existing.isPresent()) {
            existing.get().refresh(email, name);
            return existing.get();
        }
        int created = repository.insertIfAbsent(UUID.randomUUID(), jwt.getSubject(), email, name, Instant.now(clock));
        AppUser user = repository.findBySubject(jwt.getSubject()).orElseThrow();
        if (created == 1) {
            boolean verified = Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"));
            events.publishEvent(new UserProvisioned(user.getId(), email, verified));
        }
        return user;
    }

    public Optional<AppUser> findByEmail(String email) {
        return repository.findByEmailIgnoreCase(email);
    }

    public Optional<AppUser> findById(UUID id) {
        return repository.findById(id);
    }
}
