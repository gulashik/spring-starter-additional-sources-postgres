
plugins {
    /*
        НЕТ плагина org.springframework.boot. Стартер — это БИБЛИОТЕКА, а не приложение.
        Используем: обычный `java-library` — это создаёт корректный публикуемый jar с Class-Path
    */
    `java-library`                                                  // публикуем библиотеку, а не приложение
    `maven-publish`                                                 // публикация в локальный/удалённый Maven repo
    id("io.spring.dependency-management") version "1.1.6"           // BOM Spring Boot для согласованных версий
}

group = "org.gulash.demo"
version = "1.0.0"
description = "Spring Boot Starter, добавляющий несколько дополнительных PostgreSQL-источников по конфигурации"

java {
    // Toolchain — рекомендуемый способ зафиксировать целевую JDK независимо от системной.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    // публикуем исходники — полезно для пользователей starter'а
    withSourcesJar()
    // и javadoc-jar
    withJavadocJar()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.4")
    }
}

dependencies {
    // === ОБЯЗАТЕЛЬНЫЕ для стартера ===
    // spring-boot-autoconfigure: содержит @AutoConfiguration, @ConditionalOn*, базы для FailureAnalyzer и т.п.
    api("org.springframework.boot:spring-boot-autoconfigure")
    // spring-jdbc: нужен JdbcTemplate, который мы динамически регистрируем для каждого доп.источника.
    api("org.springframework:spring-jdbc")
    // HikariCP: де-факто стандарт пула соединений в Spring Boot. Версия приходит из BOM.
    api("com.zaxxer:HikariCP")
    // PostgreSQL JDBC-драйвер. api — потому что наши DataSource-бины напрямую от него зависят.
    api("org.postgresql:postgresql")

    // === ОПЦИОНАЛЬНЫЕ ===
    // Actuator — стартер умеет регистрировать HealthIndicator для каждой доп.БД, но
    // только если в classpath приложения есть actuator. Иначе авто-конфиг "выключится"
    // через @ConditionalOnClass. compileOnly — чтобы потребитель сам решал, нужен ли actuator.
    compileOnly("org.springframework.boot:spring-boot-actuator")

    // === ИНСТРУМЕНТЫ РАЗРАБОТКИ ===
    // configuration-processor: генерирует spring-configuration-metadata.json из @ConfigurationProperties.
    // генерируется в build/classes/java/main/META-INF/spring-configuration-metadata.json
    // Это даёт автодополнение свойств app.datasources.* в IDE у пользователей стартера.
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // === ТЕСТЫ ===
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        // exclude vintage-engine — оставим только JUnit 5 Jupiter.
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("org.springframework.boot:spring-boot-starter-actuator") // для теста health indicator
    testImplementation("org.assertj:assertj-core")
    // Testcontainers для проверок с реальной Postgres
    testImplementation("org.testcontainers:junit-jupiter:1.20.2")
    testImplementation("org.testcontainers:postgresql:1.20.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")  // нужно для @ConfigurationProperties (rec. конструкторное связывание)
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Подсказываем Testcontainers, где сокет (Podman / Docker Desktop). Локально пользователь
    // выставит переменные окружения сам; Gradle их прокинет в JVM теста.
    systemProperty("file.encoding", "UTF-8")
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}

// Отключаем публикацию Gradle Module Metadata (.module-файла).
// Причина: io.spring.dependency-management поставляет версии зависимостей через BOM, поэтому
// в Gradle Metadata они оказываются «без версии», и Gradle 8+ помечает это как ошибку
// валидации публикации. Для совместимости с Maven-консьюмерами нам достаточно pom.xml.
tasks.withType<GenerateModuleMetadata>().configureEach {
    enabled = false
}

// Это блок плагина `maven-publish`
// публикуется в ~/.m2/repository/org/gulash/demo/additional-sources-postgres/0.0.1-SNAPSHOT/
// что именно публикуется
//  - основной .jar
//  - зависимости из api / implementation
//  - при наличии withSourcesJar() — jar с исходниками
//  - при наличии withJavadocJar() — jar с Javadoc
// Для публикации в mavenLocal нужно явно запустить `./gradlew publishToMavenLocal` это таска плагина `maven-publish`
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            // При публикации Gradle создаёт pom.xml
            pom {
                name.set("additional-sources-postgres")
                description.set(project.description)
            }
        }
    }
}
