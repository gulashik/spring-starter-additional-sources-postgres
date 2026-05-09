package org.gulash.demo.starter.postgres.props;

import java.time.Duration;

/**
 * Настройки одного дополнительного PostgreSQL-источника. Используется в {@link AdditionalSourcesProperties#datasources()}.
 * <p>
 * {@code -parameters} (включён в build.gradle.kts) даёт корректное имя параметра в bytecode,
 *        без него Boot не сможет связать YAML-ключ с аргументом конструктора.
 * </p>
 * <h4>Пример использования</h4>
 * <pre>{@code
 * # application.yml потребителя стартера
 * app:
 *   datasources:
 *     dictionary:
 *       jdbc-url: jdbc:postgresql://localhost:5434/dictionary
 *       username: dictionary
 *       password: dictionary
 *       maximum-pool-size: 5
 *       connection-timeout: 5s
 *       read-only: true
 * }</pre>
 *
 * <h4>Подводные камни</h4>
 * <ul>
 *   <li><strong>Не путать {@code jdbc-url} с {@code url}:</strong> у Hikari ключ — именно
 *       {@code jdbcUrl}; ключ {@code url} — это устаревший {@code spring.datasource.url}.</li>
 *   <li><strong>connectionTimeout</strong> — это лимит ожидания свободного соединения из пула,
 *       а не таймаут самого SQL. Под высоким контеншеном лучше падать быстро, чем висеть.</li>
 * </ul>
 *
 * @param jdbcUrl            JDBC URL Postgres вида {@code jdbc:postgresql://host:port/db}.
 *                           Обязателен. Драйвер определяется по префиксу автоматически.
 * @param username           логин БД, обязателен.
 * @param password           пароль БД, может быть пустым (но не {@code null}); валидируется отдельно.
 * @param maximumPoolSize    максимальный размер пула Hikari.
 * @param minimumIdle        минимум простаивающих соединений. По умолчанию равно
 *                           {@code maximumPoolSize} — это рекомендация автора Hikari
 *                           (фиксированный пул проще, чем «эластичный»).
 * @param connectionTimeout  таймаут ожидания соединения из пула (не SQL!). По умолчанию 5 секунд.
 * @param readOnly           помечает соединение как read-only. Postgres использует это как
 *                           подсказку: помогает балансировщику разруливать на реплики.
 * @param schema             имя схемы по умолчанию (search_path). Полезно для dictionary/history,
 *                           если они живут в нестандартной схеме.
 * @param healthQuery        SQL для health-check. По умолчанию {@code SELECT 1}.
 */
public record DataSourceProperties(
        String jdbcUrl,
        String username,
        String password,
        Integer maximumPoolSize,
        Integer minimumIdle,
        Duration connectionTimeout,
        Boolean readOnly,
        String schema,
        String healthQuery
) {

    /** Размер пула «по умолчанию» — намеренно небольшой, чтобы стартер не съел все коннекты Postgres. */
    public static final int DEFAULT_POOL_SIZE = 5;

    /** Дефолтный health-query — лёгкий и не зависит от схемы данных. */
    public static final String DEFAULT_HEALTH_QUERY = "SELECT 1";

    /**
     * Возвращает эффективный размер пула: пользовательское значение или {@link #DEFAULT_POOL_SIZE}.
     * Метод введён, чтобы избежать «магических» {@code if (x == null) ...} в местах использования.
     */
    public int effectiveMaxPoolSize() {
        return maximumPoolSize != null ? maximumPoolSize : DEFAULT_POOL_SIZE;
    }

    /** Эффективный {@code minimumIdle}; по умолчанию = размеру пула (рекомендация HikariCP). */
    public int effectiveMinIdle() {
        return minimumIdle != null ? minimumIdle : effectiveMaxPoolSize();
    }

    /** Эффективный таймаут соединения; по умолчанию 5 секунд. */
    public Duration effectiveConnectionTimeout() {
        return connectionTimeout != null ? connectionTimeout : Duration.ofSeconds(5);
    }

    /** Read-only по умолчанию — {@code false}. */
    public boolean effectiveReadOnly() {
        return Boolean.TRUE.equals(readOnly);
    }

    /** Эффективный health-query. */
    public String effectiveHealthQuery() {
        return (healthQuery == null || healthQuery.isBlank()) ? DEFAULT_HEALTH_QUERY : healthQuery;
    }
}
