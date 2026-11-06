package dev.fuken404.opspilot.shared;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Fija la organización activa en la transacción actual. Las políticas de Row Level Security de
 * Postgres la leen: sin organización activa, las tablas de incidentes no devuelven nada.
 */
@Component
public class TenantContext {

    private final JdbcTemplate jdbc;

    TenantContext(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void enter(UUID organizationId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("La organización solo se fija dentro de una transacción.");
        }
        // is_local = true: el valor muere con la transacción y no se filtra a la siguiente petición del pool.
        jdbc.queryForObject("select set_config('app.organization_id', ?, true)", String.class, organizationId.toString());
    }
}
