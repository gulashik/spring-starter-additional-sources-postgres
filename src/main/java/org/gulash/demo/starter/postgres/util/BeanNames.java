package org.gulash.demo.starter.postgres.util;

/**
 * Утилита генерации стабильных имён бинов для каждого дополнительного источника.
 *
 * <h4>Соглашение об именах</h4>
 * <ul>
 *   <li>{@code <name>DataSource}        — {@link javax.sql.DataSource} (Hikari)</li>
 *   <li>{@code <name>JdbcTemplate}      — {@link org.springframework.jdbc.core.JdbcTemplate}</li>
 *   <li>{@code <name>NamedJdbcTemplate} — {@link org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate}</li>
 * </ul>
 *
 * <h2>Пример</h2>
 * <pre>{@code
 * @Qualifier("dictionaryJdbcTemplate")
 * private JdbcTemplate jdbc;
 * }</pre>
 */
public final class BeanNames {

    private BeanNames() {}

    public static String dataSource(String name) { return name + "DataSource"; }
    public static String jdbcTemplate(String name) { return name + "JdbcTemplate"; }
    public static String namedJdbcTemplate(String name) { return name + "NamedJdbcTemplate"; }
    public static String healthIndicator(String name) { return name + "DataSourceHealthIndicator"; }
}
