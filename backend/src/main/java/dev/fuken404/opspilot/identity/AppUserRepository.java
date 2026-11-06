package dev.fuken404.opspilot.identity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findBySubject(String subject);

    Optional<AppUser> findByEmailIgnoreCase(String email);

    /** Inserta solo si no existe. Devuelve 1 si lo creó: así dos peticiones simultáneas no chocan. */
    @Modifying
    @Query(nativeQuery = true, value = """
        insert into app_user (id, subject, email, display_name, created_at)
        values (:id, :subject, :email, :name, :now)
        on conflict (subject) do nothing""")
    int insertIfAbsent(@Param("id") UUID id, @Param("subject") String subject, @Param("email") String email,
                       @Param("name") String name, @Param("now") Instant now);
}
