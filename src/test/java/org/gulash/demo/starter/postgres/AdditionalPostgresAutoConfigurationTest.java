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
 * <p>Эти тесты — каноническая иллюстрация «правильного» подхода к тестированию стартеров.</p>
 */
class AdditionalPostgresAutoConfigurationTest {

    /**
     * Основной инструмент для тестирования авто-конфигурации.
     * Мы заранее указываем, какую именно конфигурацию (AdditionalPostgresAutoConfiguration) будем проверять.
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AdditionalPostgresAutoConfiguration.class));

    @Test
    void registersDataSourceAndJdbcTemplateForEachConfiguredEntry() {
        runner
                // Настраиваем две разные БД, чтобы проверить множественную регистрацию бинов.
                .withPropertyValues(
                        "app.datasources.dictionary.jdbc-url=jdbc:postgresql://localhost:1/dictionary",
                        "app.datasources.dictionary.username=u",
                        "app.datasources.dictionary.password=p",
                        "app.datasources.history.jdbc-url=jdbc:postgresql://localhost:1/history",
                        "app.datasources.history.username=u",
                        "app.datasources.history.password=p"
                )
                .run(ctx -> {
                    // Проверяем, что для каждой записи в конфиге создались свои DataSource.
                    assertThat(ctx).hasBean("dictionaryDataSource");
                    assertThat(ctx).hasBean("historyDataSource");
                    assertThat(ctx.getBean("dictionaryDataSource")).isInstanceOf(HikariDataSource.class);

                    // Проверяем наличие соответствующих JdbcTemplate и NamedParameterJdbcTemplate.
                    assertThat(ctx).hasBean("dictionaryJdbcTemplate");
                    assertThat(ctx).hasBean("dictionaryNamedJdbcTemplate");
                    assertThat(ctx).hasBean("historyJdbcTemplate");
                    assertThat(ctx).hasBean("historyNamedJdbcTemplate");

                    // Убеждаемся, что в контексте именно 2 бина каждого типа (для dictionary и history).
                    assertThat(ctx.getBeansOfType(DataSource.class)).hasSize(2);
                    assertThat(ctx.getBeansOfType(JdbcTemplate.class)).hasSize(2);
                    assertThat(ctx.getBeansOfType(NamedParameterJdbcTemplate.class)).hasSize(2);

                    // Важная проверка: наши дополнительные DataSource НЕ должны быть помечены как @Primary.
                    // Если какой-то из них станет Primary, он может перехватить инъекции, предназначенные
                    // для основной БД приложения, что приведет к трудноуловимым багам.
                    String[] primaryNames = ctx.getBeanFactory().getBeanNamesForType(DataSource.class);
                    for (String n : primaryNames) {
                        assertThat(ctx.getBeanFactory().getBeanDefinition(n).isPrimary()).isFalse();
                    }
                });
    }

    @Test
    void doesNothingWhenNoSourcesConfigured() {
        // Если в свойствах пусто — стартер не должен регистрировать никакие БД.
        runner.run(ctx -> {
            assertThat(ctx.getBeansOfType(DataSource.class)).isEmpty();
        });
    }

    @Test
    void failsFastWhenJdbcUrlIsMissing() {
        runner
                // Специально "забываем" указать jdbc-url.
                .withPropertyValues(
                        "app.datasources.broken.username=u",
                        "app.datasources.broken.password=p"
                )
                .run(ctx -> {
                    // Контекст не должен подняться, если конфигурация неполная.
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure())
                            .isInstanceOf(AdditionalDataSourceConfigurationException.class)
                            .hasMessageContaining("jdbc-url");
                });
    }

    @Test
    void failsFastForNonPostgresUrl() {
        runner
                // Наш стартер заточен именно под PostgreSQL.
                .withPropertyValues(
                        "app.datasources.bad.jdbc-url=jdbc:mysql://localhost/x",
                        "app.datasources.bad.username=u"
                )
                .run(ctx -> {
                    // Проверяем валидацию на уровне стартера — он должен отвергнуть MySQL URL.
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure())
                            .isInstanceOf(AdditionalDataSourceConfigurationException.class)
                            .hasMessageContaining("PostgreSQL");
                });
    }

    @Test
    void healthIndicatorIsRegisteredOnlyWhenActuatorOnClasspath() {
        // Сценарий 1: Actuator присутствует в зависимостях.
        runner
                .withPropertyValues(
                        "app.datasources.dictionary.jdbc-url=jdbc:postgresql://localhost:1/dictionary",
                        "app.datasources.dictionary.username=u",
                        "app.datasources.dictionary.password=p"
                )
                .run(ctx -> {
                    // HealthIndicator должен быть создан автоматически.
                    assertThat(ctx).hasBean("dictionaryDataSourceHealthIndicator");
                    assertThat(ctx.getBean("dictionaryDataSourceHealthIndicator"))
                            .isInstanceOf(HealthIndicator.class);
                });

        // Сценарий 2: Имитируем отсутствие Actuator в classpath.
        // Это позволяет проверить работу @ConditionalOnClass.
        runner
                .withClassLoader(new FilteredClassLoader("org.springframework.boot.actuate"))
                .withPropertyValues(
                        "app.datasources.dictionary.jdbc-url=jdbc:postgresql://localhost:1/dictionary",
                        "app.datasources.dictionary.username=u",
                        "app.datasources.dictionary.password=p"
                )
                .run(ctx -> {
                    // DataSource создался, а HealthIndicator — нет, так как Actuator "отсутствует".
                    assertThat(ctx).hasBean("dictionaryDataSource");
                    assertThat(ctx).doesNotHaveBean("dictionaryDataSourceHealthIndicator");
                });
    }
}
