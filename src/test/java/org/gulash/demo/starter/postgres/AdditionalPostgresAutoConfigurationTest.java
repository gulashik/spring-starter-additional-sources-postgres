package org.gulash.demo.starter.postgres;

import com.zaxxer.hikari.HikariDataSource;
import org.gulash.demo.starter.postgres.exception.AdditionalDataSourceConfigurationException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Демонстрационные тесты, не требующие Docker.
 *
 * <h2>ApplicationContextRunner</h2>
 * Spring Boot предоставляет «лёгкий» способ протестировать авто-конфигурации без
 * полного {@code @SpringBootTest}. Он быстро поднимает контекст с заданным
 * списком {@link AutoConfigurations}, набором свойств и classpath, и позволяет
 * проверить — какие бины зарегистрированы, какие нет, какие условия сработали.
 *
 * <p>Эти тесты — каноническая иллюстрация «правильного» подхода к тестированию
 * стартеров.
 */
class AdditionalPostgresAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AdditionalPostgresAutoConfiguration.class));

    @Test
    void registersDataSourceAndJdbcTemplateForEachConfiguredEntry() {
        runner
                .withPropertyValues(
                        "app.datasources.dictionary.jdbc-url=jdbc:postgresql://localhost:1/dictionary",
                        "app.datasources.dictionary.username=u",
                        "app.datasources.dictionary.password=p",
                        "app.datasources.history.jdbc-url=jdbc:postgresql://localhost:1/history",
                        "app.datasources.history.username=u",
                        "app.datasources.history.password=p"
                )
                .run(ctx -> {
                    // Бины DataSource зарегистрированы под предсказуемыми именами.
                    assertThat(ctx).hasBean("dictionaryDataSource");
                    assertThat(ctx).hasBean("historyDataSource");
                    assertThat(ctx.getBean("dictionaryDataSource")).isInstanceOf(HikariDataSource.class);

                    // JdbcTemplate'ы тоже на месте.
                    assertThat(ctx).hasBean("dictionaryJdbcTemplate");
                    assertThat(ctx).hasBean("dictionaryNamedJdbcTemplate");
                    assertThat(ctx).hasBean("historyJdbcTemplate");
                    assertThat(ctx).hasBean("historyNamedJdbcTemplate");

                    // По типу должны находиться оба DataSource.
                    assertThat(ctx.getBeansOfType(DataSource.class)).hasSize(2);
                    assertThat(ctx.getBeansOfType(JdbcTemplate.class)).hasSize(2);
                    assertThat(ctx.getBeansOfType(NamedParameterJdbcTemplate.class)).hasSize(2);

                    // primary НЕ должен быть установлен — иначе сломали бы основной DataSource приложения.
                    String[] primaryNames = ctx.getBeanFactory().getBeanNamesForType(DataSource.class);
                    for (String n : primaryNames) {
                        assertThat(ctx.getBeanFactory().getBeanDefinition(n).isPrimary()).isFalse();
                    }
                });
    }

    @Test
    void doesNothingWhenNoSourcesConfigured() {
        runner.run(ctx -> {
            assertThat(ctx.getBeansOfType(DataSource.class)).isEmpty();
        });
    }

    @Test
    void failsFastWhenJdbcUrlIsMissing() {
        runner
                .withPropertyValues(
                        "app.datasources.broken.username=u",
                        "app.datasources.broken.password=p"
                )
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure())
                            .isInstanceOf(AdditionalDataSourceConfigurationException.class)
                            .hasMessageContaining("jdbc-url");
                });
    }

    @Test
    void failsFastForNonPostgresUrl() {
        runner
                .withPropertyValues(
                        "app.datasources.bad.jdbc-url=jdbc:mysql://localhost/x",
                        "app.datasources.bad.username=u"
                )
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure())
                            .isInstanceOf(AdditionalDataSourceConfigurationException.class)
                            .hasMessageContaining("PostgreSQL");
                });
    }

    @Test
    void healthIndicatorIsRegisteredOnlyWhenActuatorOnClasspath() {
        // Здесь Actuator есть (он в testImplementation):
        runner
                .withPropertyValues(
                        "app.datasources.dictionary.jdbc-url=jdbc:postgresql://localhost:1/dictionary",
                        "app.datasources.dictionary.username=u",
                        "app.datasources.dictionary.password=p"
                )
                .run(ctx -> {
                    assertThat(ctx).hasBean("dictionaryDataSourceHealthIndicator");
                    assertThat(ctx.getBean("dictionaryDataSourceHealthIndicator"))
                            .isInstanceOf(HealthIndicator.class);
                });

        // А теперь «спрячем» Actuator из classpath с помощью FilteredClassLoader —
        // health-indicator не должен регистрироваться. Это демонстрирует @ConditionalOnClass.
        runner
                .withClassLoader(new FilteredClassLoader("org.springframework.boot.actuate"))
                .withPropertyValues(
                        "app.datasources.dictionary.jdbc-url=jdbc:postgresql://localhost:1/dictionary",
                        "app.datasources.dictionary.username=u",
                        "app.datasources.dictionary.password=p"
                )
                .run(ctx -> {
                    assertThat(ctx).hasBean("dictionaryDataSource");
                    assertThat(ctx).doesNotHaveBean("dictionaryDataSourceHealthIndicator");
                });
    }
}
