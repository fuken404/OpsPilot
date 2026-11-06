package dev.fuken404.opspilot.demo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fuken404.opspilot.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("demo")
@SpringBootTest(properties = {"opspilot.realtime.broker=memory", "opspilot.sla.scheduler-enabled=false",
    "opspilot.mail.dispatcher-enabled=false"})
class DemoDataTests {

    @Autowired
    ApplicationRunner demoSeed;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transaction;

    int tickets(String slug) {
        String org = jdbc.queryForObject("select id::text from organization where slug = ?", String.class, slug);
        return transaction.execute(tx -> {
            jdbc.queryForObject("select set_config('app.organization_id', ?, true)", String.class, org);
            return jdbc.queryForObject("select count(*) from ticket", Integer.class);
        });
    }

    /** La demo arranca con datos (la siembra ya corrió al iniciar) y volver a correrla no duplica nada. */
    @Test
    void siembraUnaSolaVezConLosSubDelRealm() throws Exception {
        demoSeed.run(new DefaultApplicationArguments());

        assertThat(jdbc.queryForObject("select count(*) from app_user where subject like '4f1d2c3a-%'", Integer.class))
            .isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from membership", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from invitation", Integer.class)).isZero();
        assertThat(tickets("acme")).isEqualTo(4);
        assertThat(tickets("nimbus")).isEqualTo(1);
    }
}
