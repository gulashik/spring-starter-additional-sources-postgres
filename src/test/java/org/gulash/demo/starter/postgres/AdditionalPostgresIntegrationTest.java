package org.gulash.demo.starter.postgres;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционный тест: реальная Postgres через Testcontainers.
 *
 * <h2>Что демонстрирует</h2>
 * Что зарегистрированный стартером {@code JdbcTemplate} действительно подключается
 * к настоящей PostgreSQL и выполняет SQL.
 *
 * <h2>Требования</h2>
 * <ul>
 *   <li>Запущенный Docker / Docker-совместимый рантайм (например, Podman). Иначе
 *       Testcontainers выбросит {@code IllegalStateException} и тест будет SKIPPED
 *       (если используется {@code @EnabledIfSystemProperty}). Здесь — упадёт явно,
 *       это намеренно, чтобы пользователь не пропустил факт «нет Docker».</li>
 *   <li>Для Podman: установите переменную окружения
 *       {@code DOCKER_HOST=unix:///path/to/podman.sock} (см. README).</li>
 * </ul>
 *
 * <h2>Подводные камни</h2>
 * <ul>
 *   <li>Контейнер стартует ~1–3 секунды; не злоупотребляйте Testcontainers'ом в unit-тестах.</li>
 *   <li>{@code @Testcontainers} управляет жизненным циклом — без него контейнер не остановится.</li>
 *   <li>Для CI лучше использовать {@code reuse=true} и {@code TESTCONTAINERS_REUSE_ENABLE=true},
 *       но это требует дополнительной настройки в {@code ~/.testcontainers.properties}.</li>
 * </ul>
 */
@Testcontainers
class AdditionalPostgresIntegrationTest {

    // Описываем контейнер PostgreSQL. Используем статическое поле и @Container,
    // чтобы Testcontainers управлял его жизненным циклом (запуск перед тестами, остановка после).
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16.4-alpine"))
            .withDatabaseName("dictionary")
            .withUsername("dictionary")
            .withPassword("dictionary");

    @Test
    void registeredJdbcTemplateActuallyTalksToPostgres() {
        // ApplicationContextRunner — это утилита Spring Boot для тестирования автоконфигураций.
        // Она позволяет «на лету» создать контекст с нужными настройками.
        new ApplicationContextRunner()
                // Указываем нашу автоконфигурацию, которую хотим проверить.
                .withConfiguration(AutoConfigurations.of(AdditionalPostgresAutoConfiguration.class))
                // Передаем свойства, имитируя application.yml/properties.
                // Используем динамические параметры запущенного Testcontainers (порт, url).
                .withPropertyValues(
                        "app.datasources.dictionary.jdbc-url=" + postgres.getJdbcUrl(),
                        "app.datasources.dictionary.username=" + postgres.getUsername(),
                        "app.datasources.dictionary.password=" + postgres.getPassword(),
                        "app.datasources.dictionary.maximum-pool-size=2"
                )
                .run(ctx -> {
                    // Проверяем, что бин JdbcTemplate создался с правильным именем и работает.
                    JdbcTemplate jdbc = ctx.getBean("dictionaryJdbcTemplate", JdbcTemplate.class);
                    // Выполняем реальный запрос к БД. SELECT 1 — самый простой способ проверить живое соединение.
                    Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
                    assertThat(one).isEqualTo(1);

                    // Убеждаемся, что наш кастомный HealthIndicator тоже зарегистрировался
                    // и возвращает статус UP для работающей базы.
                    var health = ctx.getBean("dictionaryDataSourceHealthIndicator",
                            org.springframework.boot.actuate.health.HealthIndicator.class).health();
                    assertThat(health.getStatus().getCode()).isEqualTo("UP");
                });
    }
}
