package jornal.common.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseUrlEnvironmentPostProcessorTest {

    @Test
    void convertsRenderUrlToJdbc() {
        var props = DatabaseUrlEnvironmentPostProcessor.toDatasource(
                "postgresql://jornal_user:p%40ss:word@dpg-abc123-a/jornal_db");
        assertThat(props).containsEntry("spring.datasource.url", "jdbc:postgresql://dpg-abc123-a:5432/jornal_db")
                .containsEntry("spring.datasource.username", "jornal_user")
                .containsEntry("spring.datasource.password", "p@ss:word");
    }

    @Test
    void keepsPortAndQuery() {
        assertThat(DatabaseUrlEnvironmentPostProcessor.toDatasource("postgres://u:p@host:6543/db?sslmode=require"))
                .containsEntry("spring.datasource.url", "jdbc:postgresql://host:6543/db?sslmode=require");
    }

    @Test
    void ignoresOtherUrls() {
        assertThat(DatabaseUrlEnvironmentPostProcessor.toDatasource("jdbc:postgresql://host/db")).isEmpty();
    }
}
