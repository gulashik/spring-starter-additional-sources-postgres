package org.gulash.demo.starter.postgres;

import com.zaxxer.hikari.HikariDataSource;
import org.gulash.demo.starter.postgres.actuactor.AdditionalDataSourceHealthIndicator;
import org.gulash.demo.starter.postgres.props.AdditionalSourcesProperties;
import org.gulash.demo.starter.postgres.registration.AdditionalDataSourceRegistrar;
import org.gulash.demo.starter.postgres.util.BeanNames;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

/**
 * <h4>Авто-конфигурация стартера {@code additional-sources-postgres}.</h4>
 * <h4>Точка входа стартера</h4>
 * Этот класс регистрируется через файл {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * — это <strong>современный</strong> механизм Spring Boot 3.x.
 *
 * <h4>Аннотация {@link AutoConfiguration}</h4>
 * Это «специализированный» {@code @Configuration} для авто-конфигов.
 * Главное отличие — порядок применения и поддержка атрибутов {@code before/after}, которые здесь и
 * используем: наш конфиг должен отработать <strong>до</strong> {@link DataSourceAutoConfiguration},
 * иначе Spring Boot создаст «основной» DataSource раньше, и в некоторых сценариях
 * (например, при отсутствии основной spring.datasource.url) это приведёт к падению старта.
 *
 * <h4>Структура</h4>
 * <ol>
 *   <li>{@link AdditionalDataSourceRegistrar} — регистрирует {@code DataSource}/{@code JdbcTemplate}
 *       динамически по карте {@code app.datasources.*};</li>
 *   <li>Вложенная {@link ActuatorIntegration} — регистрирует
 *       {@link AdditionalDataSourceHealthIndicator} ТОЛЬКО если на classpath есть Actuator
 *       (через {@link ConditionalOnClass}).</li>
 * </ol>
 *
 * <h4>Условия активации</h4>
 * <ul>
 *   <li>{@link ConditionalOnClass}({@link HikariDataSource}) — без Hikari в classpath
 *       стартер не активируется (чтобы не падать с {@code NoClassDefFoundError}).</li>
 *   <li>Если карта {@code app.datasources} пустая или отсутствует — Registrar
 *       просто не регистрирует ни одного бина (нулевой эффект). Это безопасный дефолт:
 *       нулевой ущерб, если стартер случайно подтянут как транзитивная зависимость.</li>
 * </ul>
 *
 * <h3>Пример минимальной конфигурации потребителя</h3>
 * <pre>{@code
 * # build.gradle.kts
 * dependencies {
 *     implementation("org.gulash.demo:additional-sources-postgres:0.0.1-SNAPSHOT")
 * }
 *
 * # application.yml
 * app:
 *   datasources:
 *     dictionary:
 *       jdbc-url: jdbc:postgresql://localhost:5434/dictionary
 *       username: dictionary
 *       password: dictionary
 * }</pre>
 */
@AutoConfiguration(before = DataSourceAutoConfiguration.class)
@ConditionalOnClass(HikariDataSource.class)
@EnableConfigurationProperties(AdditionalSourcesProperties.class)
public class AdditionalPostgresAutoConfiguration {

    /**
     * {@link AdditionalDataSourceRegistrar} обязан быть {@code static @Bean}, так как это
     * {@code BeanDefinitionRegistryPostProcessor}: иначе Spring выдаст предупреждение
     * «не получится применить @Configuration-обработку к классу с post-processor'ом».
     */
    @Bean
    public static AdditionalDataSourceRegistrar additionalDataSourceRegistrar(Environment environment) {
        return new AdditionalDataSourceRegistrar(environment);
    }

    /**
     * Вложенный конфиг для интеграции с Actuator. Срабатывает ТОЛЬКО при наличии
     * {@link org.springframework.boot.actuate.health.HealthIndicator}.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    static class ActuatorIntegration {

        /**
         * Регистрирует health-indicators для каждого зарегистрированного источника.
         * Делается ПОСЛЕ старта контекста, потому что нужно «знать» уже созданные
         * DataSource-бины. Используем событие {@link ContextRefreshedEvent} — это
         * поздняя, но корректная точка для введения дополнительных бинов.
         *
         * Альтернатива — {@code SmartInitializingSingleton}; однако проще и понятнее
         * читать через event-listener.
         */
        @Bean
        public HealthIndicatorRegistrar healthIndicatorRegistrar(ApplicationContext context,
                                                                 AdditionalSourcesProperties props) {
            return new HealthIndicatorRegistrar(context, props);
        }

        static class HealthIndicatorRegistrar {
            private final ApplicationContext ctx;
            private final AdditionalSourcesProperties props;

            HealthIndicatorRegistrar(ApplicationContext ctx, AdditionalSourcesProperties props) {
                this.ctx = ctx;
                this.props = props;
            }

            @EventListener(ContextRefreshedEvent.class)
            void onContextRefreshed() {
                if (!(ctx.getAutowireCapableBeanFactory() instanceof DefaultListableBeanFactory bf)) {
                    return;
                }
                BeanDefinitionRegistry registry = (BeanDefinitionRegistry) bf;
                props.datasources().forEach((name, dsProps) -> {
                    String beanName = BeanNames.healthIndicator(name);
                    if (registry.containsBeanDefinition(beanName)) {
                        return; // не дублируем, если уже есть
                    }
                    BeanDefinition def = BeanDefinitionBuilder
                            .genericBeanDefinition(AdditionalDataSourceHealthIndicator.class)
                            .addConstructorArgReference(BeanNames.dataSource(name))
                            .addConstructorArgValue(dsProps.effectiveHealthQuery())
                            .getBeanDefinition();
                    registry.registerBeanDefinition(beanName, def);
                    // Эксплицитно создаём, чтобы /actuator/health сразу его увидел.
                    bf.getBean(beanName);
                });
            }
        }
    }
}
