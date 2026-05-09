package org.gulash.demo.starter.postgres.registration;

import com.zaxxer.hikari.HikariDataSource;
import org.gulash.demo.starter.postgres.exception.AdditionalDataSourceConfigurationException;
import org.gulash.demo.starter.postgres.props.AdditionalSourcesProperties;
import org.gulash.demo.starter.postgres.props.DataSourceProperties;
import org.gulash.demo.starter.postgres.util.BeanNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Map;

/**
 * Динамическая регистрация бинов {@link javax.sql.DataSource} / {@link JdbcTemplate} /
 * {@link NamedParameterJdbcTemplate} на основе {@link AdditionalSourcesProperties}.
 *
 * <h2>Почему именно {@link BeanDefinitionRegistryPostProcessor}</h2>
 * Аннотация {@code @Bean} умеет регистрировать только заранее известные бины. Когда
 * количество и имена бинов диктуются конфигурацией (а не кодом), нужен механизм
 * программной регистрации. У нас два штатных варианта:
 * <ol>
 *   <li>{@link org.springframework.context.annotation.ImportBeanDefinitionRegistrar}
 *       — удобен, но привязан к {@code @Import} и аннотации-маркеру;</li>
 *   <li>{@link BeanDefinitionRegistryPostProcessor} — самый ранний хук в жизненном
 *       цикле контекста. Идеален для авто-конфигурации стартера, потому что
 *       <strong>выполняется до</strong> регистрации обычных {@code @Configuration}-классов
 *       и до создания основного {@code DataSource} Spring Boot.</li>
 * </ol>
 *
 * <h2>Жизненный цикл</h2>
 * <pre>
 *   ApplicationContext начинает refresh()
 *     -> вызываются BeanFactoryPostProcessors
 *        -> сначала BeanDefinitionRegistryPostProcessor.postProcessBeanDefinitionRegistry()  ← здесь мы
 *        -> затем BeanFactoryPostProcessor.postProcessBeanFactory()
 *     -> создание singletons
 * </pre>
 * Наш processor читает {@link Environment}, биндит {@link AdditionalSourcesProperties}
 * <strong>вручную</strong> через {@link Binder} (на этом этапе нет ещё бина-«биндера»),
 * и регистрирует {@link BeanDefinition} с {@code destroyMethod = "close"} — чтобы
 * Hikari корректно закрывал пулы при остановке приложения.
 *
 * <h2>Подводные камни и решения</h2>
 * <ul>
 *   <li><strong>Нельзя инжектить бины</strong> в {@code BeanDefinitionRegistryPostProcessor} —
 *       они ещё не созданы. Поэтому используем {@code EnvironmentAware} и {@code Binder.get()}.</li>
 *   <li><strong>destroyMethodName=close</strong> — без него Hikari будет «утекать» при
 *       остановке контекста, особенно в тестах с пере-загрузкой контекста.</li>
 *   <li><strong>{@code primary = false}</strong> — обязательно. Иначе наш DataSource
 *       будет конкурировать с основным «spring.datasource» и сломает {@code @Autowired DataSource}.</li>
 *   <li><strong>Имя бина = из конфигурации</strong>; делаем явные {@code @Qualifier}-имена
 *       детерминированными через {@link BeanNames}.</li>
 * </ul>
 *
 * <h2>Пример результата</h2>
 * При конфигурации с двумя источниками {@code dictionary} и {@code history} в контексте
 * появляются бины:
 * <pre>{@code
 *   dictionaryDataSource           : HikariDataSource
 *   dictionaryJdbcTemplate         : JdbcTemplate
 *   dictionaryNamedJdbcTemplate    : NamedParameterJdbcTemplate
 *   historyDataSource              : HikariDataSource
 *   historyJdbcTemplate            : JdbcTemplate
 *   historyNamedJdbcTemplate       : NamedParameterJdbcTemplate
 * }</pre>
 */
public class AdditionalDataSourceRegistrar implements BeanDefinitionRegistryPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(AdditionalDataSourceRegistrar.class);

    private final Environment environment;

    public AdditionalDataSourceRegistrar(Environment environment) {
        this.environment = environment;
    }

    /**
     * Вызывается очень рано при запуске Spring-контекста, ещё до создания обычных бинов.
     * Его задача — дать возможность программно зарегистрировать новые BeanDefinition, то есть описания будущих бинов.
     * */
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        log.debug("additional-sources-postgres: начало регистрации дополнительных источников данных");
        AdditionalSourcesProperties props = Binder.get(environment)
                // Из environment(в т.ч. application.yml), начинающиеся с "app" собрать объект AdditionalSourcesProperties
                .bind("app", AdditionalSourcesProperties.class)
                .orElseGet(() -> new AdditionalSourcesProperties(Map.of()));

        Map<String, DataSourceProperties> sources = props.datasources();
        if (sources.isEmpty()) {
            log.debug("additional-sources-postgres: нет ни одного источника, пропуск регистрации");
            return;
        }

        sources.forEach((name, ds) -> registerOne(registry, name, ds));
    }

    private void registerOne(BeanDefinitionRegistry registry, String name, DataSourceProperties ds) {
        validate(name, ds);

        // 1) DataSource (HikariDataSource). Используем factory-метод — он явно создаёт
        //    бин и позволяет легко передать аргументы. destroy-method = close обязателен.
        AbstractBeanDefinition dataSourceDef = BeanDefinitionBuilder
                .genericBeanDefinition(HikariDataSource.class, () -> buildHikari(name, ds))
                .setDestroyMethodName("close")
                .setLazyInit(false)
                .getBeanDefinition();
        dataSourceDef.setPrimary(false);
        registry.registerBeanDefinition(BeanNames.dataSource(name), dataSourceDef);

        // 2) JdbcTemplate(name + "DataSource") — связываем по имени бина.
        AbstractBeanDefinition jdbcDef = BeanDefinitionBuilder
                .genericBeanDefinition(JdbcTemplate.class)
                .addConstructorArgReference(BeanNames.dataSource(name))
                .getBeanDefinition();
        registry.registerBeanDefinition(BeanNames.jdbcTemplate(name), jdbcDef);

        // 3) NamedParameterJdbcTemplate
        AbstractBeanDefinition namedDef = BeanDefinitionBuilder
                .genericBeanDefinition(NamedParameterJdbcTemplate.class)
                .addConstructorArgReference(BeanNames.dataSource(name))
                .getBeanDefinition();
        registry.registerBeanDefinition(BeanNames.namedJdbcTemplate(name), namedDef);

        log.info("additional-sources-postgres: зарегистрирован источник '{}' -> {}", name, ds.jdbcUrl());
    }

    /**
     * Старая сигнатура {@code postProcessBeanFactory} в Spring 6.x помечена как default-метод
     * в интерфейсе {@code BeanDefinitionRegistryPostProcessor}, поэтому переопределять не нужно.
     * Оставлено для документации.
     */
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // no-op: вся логика выполнена раньше, в postProcessBeanDefinitionRegistry
    }

    /* ============================================================================== */

    private static HikariDataSource buildHikari(String name, DataSourceProperties ds) {
        HikariDataSource hikari = new HikariDataSource();
        hikari.setPoolName("hikari-" + name);
        hikari.setJdbcUrl(ds.jdbcUrl());
        hikari.setUsername(ds.username());
        hikari.setPassword(ds.password());
        hikari.setMaximumPoolSize(ds.effectiveMaxPoolSize());
        hikari.setMinimumIdle(ds.effectiveMinIdle());
        hikari.setConnectionTimeout(ds.effectiveConnectionTimeout().toMillis());
        hikari.setReadOnly(ds.effectiveReadOnly());
        if (ds.schema() != null && !ds.schema().isBlank()) {
            hikari.setSchema(ds.schema());
        }
        // НЕ задаём driverClassName: Hikari определит его по jdbc-url. Так совместимее.
        // НЕ ставим autoCommit=false по умолчанию — это меняет поведение и часто ломает наивный JDBC код.
        return hikari;
    }

    private static void validate(String name, DataSourceProperties ds) {
        if (name == null || name.isBlank()) {
            throw new AdditionalDataSourceConfigurationException(name,
                    "имя источника не должно быть пустым (ключ карты app.datasources.*)");
        }
        if (ds == null) {
            throw new AdditionalDataSourceConfigurationException(name,
                    "свойства источника не заданы");
        }
        if (ds.jdbcUrl() == null || ds.jdbcUrl().isBlank()) {
            throw new AdditionalDataSourceConfigurationException(name,
                    "не задан jdbc-url; пример: jdbc:postgresql://host:5432/db");
        }
        if (!ds.jdbcUrl().startsWith("jdbc:postgresql:")) {
            throw new AdditionalDataSourceConfigurationException(name,
                    "стартер поддерживает только PostgreSQL: jdbc-url должен начинаться с 'jdbc:postgresql:', " +
                            "получено: " + ds.jdbcUrl());
        }
        if (ds.username() == null || ds.username().isBlank()) {
            throw new AdditionalDataSourceConfigurationException(name,
                    "не задан username");
        }
        if (ds.maximumPoolSize() != null && ds.maximumPoolSize() <= 0) {
            throw new AdditionalDataSourceConfigurationException(name,
                    "maximum-pool-size должен быть положительным числом");
        }
    }
}
