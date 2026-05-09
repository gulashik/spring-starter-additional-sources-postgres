
Spring Boot Starter, добавляющий приложению произвольное количество дополнительных PostgreSQL-источников через YAML-конфигурацию application.yml.

### Подключение в потребителе (Gradle)
#### build.gradle.kts
```kotlin
 dependencies {
     implementation("org.gulash.demo:additional-sources-postgres:1.0.0")
 }
```
#### settings.gradle.kts
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal() // здесь живёт наш стартер после publishToMavenLocal
        mavenCentral()
    }
}
```

#### application.yml
```yaml
app:
  datasources:
    dictionary:
      jdbc-url: jdbc:postgresql://localhost:5434/dictionary
      username: dictionary
      password: dictionary
    history:
      jdbc-url: jdbc:postgresql://localhost:5435/history
      username: history
      password: history
```

#### Использование в коде
```java
@Repository
class DictionaryDao {
    private final JdbcTemplate jdbc;
    DictionaryDao(@Qualifier("dictionaryJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
}
```

#### Что регистрируется

Для каждой записи map-ы `app.datasources.<name>` стартер создаёт три бина:

| Имя бина                      | Тип                                |
| ----------------------------- | ---------------------------------- |
| `<name>DataSource`            | `HikariDataSource`                 |
| `<name>JdbcTemplate`          | `JdbcTemplate`                     |
| `<name>NamedJdbcTemplate`     | `NamedParameterJdbcTemplate`       |

При наличии Spring Boot Actuator также регистрируется
`<name>DataSourceHealthIndicator` и появляется в `/actuator/health` под
`components.db.components.<name>DataSource`.



## Опциональные поля конфигурации

| Свойство               | Значение по умолчанию | Назначение                                |
| ---------------------- | --------------------- | ----------------------------------------- |
| `maximum-pool-size`    | 5                     | максимум соединений Hikari                |
| `minimum-idle`         | = `maximum-pool-size` | рекомендация HikariCP — фиксированный пул |
| `connection-timeout`   | 5s                    | таймаут ожидания соединения из пула       |
| `read-only`            | false                 | помечает соединения как read-only         |
| `schema`               | (без изменений)       | дефолтная схема (search_path)             |
| `health-query`         | `SELECT 1`            | SQL для health-indicator                  |

