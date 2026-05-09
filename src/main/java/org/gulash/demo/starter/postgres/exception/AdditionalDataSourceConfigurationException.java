package org.gulash.demo.starter.postgres.exception;

/**
 * Доменная ошибка стартера: «конфигурация дополнительного источника невалидна».
 *
 * <h2>Зачем отдельный тип</h2>
 * Spring Boot ловит {@link Throwable} на старте и пытается найти
 * {@link org.springframework.boot.diagnostics.FailureAnalyzer} для конкретного класса.
 * Свой тип-исключения = свой красивый failure-output вместо «голого» стектрейса.
 *
 * <h2>Когда выбрасывается</h2>
 * <ul>
 *   <li>не задан {@code jdbc-url} или {@code username};</li>
 *   <li>имя источника пусто/невалидно;</li>
 *   <li>невалидное значение пула (например, {@code maximum-pool-size <= 0}).</li>
 * </ul>
 */
public class AdditionalDataSourceConfigurationException extends RuntimeException {

    private final String dataSourceName;

    public AdditionalDataSourceConfigurationException(String dataSourceName, String message) {
        super(message);
        this.dataSourceName = dataSourceName;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }
}
