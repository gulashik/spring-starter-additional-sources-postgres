package org.gulash.demo.starter.postgres.actuactor;

import org.gulash.demo.starter.postgres.util.BeanNames;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Простой Actuator-индикатор «жив ли источник».
 *
 * <h2>Почему свой, а не {@code DataSourceHealthIndicator} из Boot</h2>
 * {@code DataSourceHealthIndicator} умеет почти всё то же самое, но:
 * <ul>
 *   <li>при динамической регистрации нескольких DataSource'ов Boot не знает их имена,
 *       и стандартная авто-конфигурация {@code DataSourceHealthIndicatorAutoConfiguration}
 *       создаёт индикатор только для «основного»;</li>
 *   <li>здесь же мы создаём индикатор на каждый бин из стартера и даём ему правильное
 *       имя (через {@link BeanNames#healthIndicator}). В выводе {@code /actuator/health}
 *       это превращается в раздел {@code dictionaryDataSource}, {@code historyDataSource}.</li>
 * </ul>
 *
 * <h2>Пример вывода</h2>
 * <pre>{@code
 * GET /actuator/health
 * {
 *   "status": "UP",
 *   "components": {
 *     "dictionaryDataSource": { "status": "UP", "details": { "result": 1, "query": "SELECT 1" } },
 *     "historyDataSource":    { "status": "UP", "details": { "result": 1, "query": "SELECT 1" } }
 *   }
 * }
 * }</pre>
 */
public class AdditionalDataSourceHealthIndicator extends AbstractHealthIndicator {

    private final DataSource dataSource;
    private final String query;

    public AdditionalDataSourceHealthIndicator(DataSource dataSource, String query) {
        super("DataSource health check failed");
        this.dataSource = dataSource;
        this.query = query;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        // try-with-resources обязательно, иначе утечка соединений в пуле.
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            Object result = rs.next() ? rs.getObject(1) : null;
            builder.up().withDetail("query", query).withDetail("result", result);
        }
    }
}
