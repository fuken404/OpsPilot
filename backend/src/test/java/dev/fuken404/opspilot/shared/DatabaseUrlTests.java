package dev.fuken404.opspilot.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class DatabaseUrlTests {

    @Test
    void convierteLaUrlDeNeonADirectaYJdbc() {
        var connection = DatabaseUrlEnvironmentPostProcessor.parse(
            "postgresql://neondb_owner:p%40ss@ep-cool-123-pooler.us-east-2.aws.neon.tech/opspilot?sslmode=require&channel_binding=require")
            .orElseThrow();
        assertThat(connection.jdbcUrl())
            .isEqualTo("jdbc:postgresql://ep-cool-123.us-east-2.aws.neon.tech/opspilot?sslmode=require");
        assertThat(connection.username()).isEqualTo("neondb_owner");
        assertThat(connection.password()).isEqualTo("p@ss");
    }

    @Test
    void prefiereLaUrlSinPoolerYRespetaJdbc() {
        MockEnvironment env = new MockEnvironment()
            .withProperty("DATABASE_URL", "postgres://u:x@ep-a-pooler.aws.neon.tech/db")
            .withProperty("DATABASE_URL_UNPOOLED", "postgres://u:y@ep-a.aws.neon.tech:5432/db");
        new DatabaseUrlEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication());
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://ep-a.aws.neon.tech:5432/db?sslmode=require");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("y");

        MockEnvironment jdbc = new MockEnvironment().withProperty("DATABASE_URL", "jdbc:postgresql://h/db");
        new DatabaseUrlEnvironmentPostProcessor().postProcessEnvironment(jdbc, new SpringApplication());
        assertThat(jdbc.getProperty("spring.datasource.url")).isNull();
    }

    @Test
    void limpiaLoQueSeCuelaAlCopiar() {
        assertThat(DatabaseUrlEnvironmentPostProcessor.clean("psql 'postgresql://u:p@h.neon.tech/db?sslmode=require'"))
            .isEqualTo("postgresql://u:p@h.neon.tech/db?sslmode=require");
        assertThat(DatabaseUrlEnvironmentPostProcessor.clean("  \"jdbc:postgresql://h/db?sslmode=require\" "))
            .isEqualTo("jdbc:postgresql://h/db?sslmode=require");
        assertThat(DatabaseUrlEnvironmentPostProcessor.clean("DATABASE_URL=postgres://u:p@h/db"))
            .isEqualTo("postgres://u:p@h/db");
    }

    @Test
    void laUrlDeUpstashSiempreVaConTls() {
        assertThat(DatabaseUrlEnvironmentPostProcessor.cleanRedis("redis-cli --tls -u redis://default:k@x.upstash.io:6379"))
            .isEqualTo("rediss://default:k@x.upstash.io:6379");
        assertThat(DatabaseUrlEnvironmentPostProcessor.cleanRedis("redis://localhost:6379"))
            .isEqualTo("redis://localhost:6379");
    }
}
