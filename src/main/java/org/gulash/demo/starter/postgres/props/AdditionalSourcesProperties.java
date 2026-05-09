package org.gulash.demo.starter.postgres.props;

import org.gulash.demo.starter.postgres.AdditionalPostgresAutoConfiguration;
import org.gulash.demo.starter.postgres.registration.AdditionalDataSourceRegistrar;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Корневые свойства стартера: карта именованных дополнительных источников данных.
 *
 * <h2>Префикс {@code app.datasources}</h2>
 * Намеренно НЕ используется {@code spring.datasource.*}: этот префикс зарезервирован
 * Spring Boot Auto-Configuration под «основной» {@code DataSource}. Если стартер
 * подмешает в него свои ключи — могут конфликтовать имена бинов, биндинг и
 * {@code @ConditionalOnProperty}. Поэтому всегда используем СВОЙ префикс.
 *
 * <h2>Почему {@code Map<String, DataSourceProperties>}</h2>
 * Количество дополнительных БД заранее неизвестно (в проекте dwh их две, в другом
 * проекте их может быть пять). Это и есть причина, по которой регистрация бинов
 * выполняется через {@link org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor}
 * (см. {@link AdditionalDataSourceRegistrar}) — ни одна из «классических» аннотаций
 * {@code @Bean} такой динамики не даёт.
 *
 * <h2>Пример конфигурации</h2>
 * <pre>{@code
 * app:
 *   datasources:
 *     dictionary:
 *       jdbc-url: jdbc:postgresql://localhost:5434/dictionary
 *       username: dictionary
 *       password: dictionary
 *     history:
 *       jdbc-url: jdbc:postgresql://localhost:5435/history
 *       username: history
 *       password: history
 * }</pre>
 *
 * <h2>Как пользователь получает бины</h2>
 * <pre>{@code
 * @Service
 * class DictionaryDao {
 *     private final JdbcTemplate jdbc;
 *     DictionaryDao(@Qualifier("dictionaryJdbcTemplate") JdbcTemplate jdbc) {
 *         this.jdbc = jdbc;
 *     }
 * }
 * }</pre>
 *
 * <h2>Подводные камни</h2>
 * <ul>
 *   <li>Имя ключа в карте ({@code dictionary}, {@code history}) становится частью имени
 *       бина: {@code dictionaryDataSource}, {@code dictionaryJdbcTemplate}. Поэтому имена
 *       должны быть валидными Java-идентификаторами и уникальными.</li>
 *   <li>Если карта пустая — стартер «деактивируется», бины не регистрируются. Это
 *       проверено через {@code @ConditionalOnProperty(prefix = "app.datasources")} в
 *       {@link AdditionalPostgresAutoConfiguration}.</li>
 *   <li>{@link LinkedHashMap} как тип по умолчанию сохраняет порядок объявления —
 *       полезно при логировании и health-output (детерминированный вывод).</li>
 * </ul>
 *
 * @param datasources map-а «логическое имя -> свойства источника»
 */
@ConfigurationProperties(prefix = "app")
public record AdditionalSourcesProperties(
    Map<String, DataSourceProperties> datasources
) {
    /**
     * Канонизирует {@code null} в пустую карту, чтобы потребители не падали с NPE.
     */
    public AdditionalSourcesProperties {
        datasources = (datasources == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(datasources);
    }
}
