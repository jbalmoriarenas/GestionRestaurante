# GestionRestauranteBack

Backend de GestionRestaurante, generado con [Spring Initializr](https://start.spring.io) sobre Spring Boot 4 / Spring Framework 7.

## Tecnologías y versiones

| Categoría              | Tecnología                | Versión         |
|------------------------|----------------------------|-----------------|
| Framework              | Spring Boot                | 4.1.1           |
| Framework base         | Spring Framework           | 7.0.9           |
| Lenguaje / plataforma  | Java (JDK)                 | 21 (Eclipse Temurin 21.0.12.1) |
| Build tool             | Maven (wrapper `mvnw`)     | 3.9.16          |
| Persistencia           | Spring Data JPA            | 4.1.1           |
| ORM                    | Hibernate ORM              | 7.4.5.Final     |
| Base de datos          | MySQL (driver JDBC `mysql-connector-j`) | 9.7.0 |
| Servidor embebido      | Tomcat embebido            | 11.0.24         |
| Serialización JSON     | Jackson (Jackson 3, `tools.jackson.core`) | 3.1.5 |
| Validación             | Jakarta Bean Validation / Hibernate Validator | 9.1.3.Final |
| Boilerplate            | Lombok                     | 1.18.46         |

El JDK 21 está **embebido en el propio proyecto** (`.jdk/`, no versionado en git) y no depende del Java instalado a nivel de sistema: `mvnw` / `mvnw.cmd` fuerzan automáticamente ese `JAVA_HOME` al ejecutarse desde esta carpeta, para no interferir con otros proyectos que usen versiones de Java más antiguas.

## Starters de Spring Boot incluidos

- `spring-boot-starter-webmvc` — API REST (Spring MVC + Tomcat embebido)
- `spring-boot-starter-data-jpa` — persistencia con Spring Data JPA / Hibernate
- `spring-boot-starter-validation` — validación de beans (Jakarta Validation)
- `com.mysql:mysql-connector-j` — driver JDBC de MySQL (scope `runtime`)
- `org.projectlombok:lombok` — reduce boilerplate (getters/setters/constructores)
- Starters `-test` equivalentes (`webmvc-test`, `data-jpa-test`, `validation-test`) para pruebas

## Configuración del proyecto

- `groupId`: `com.gestionrestaurante`
- `artifactId`: `gestion-restaurante-back`
- `packaging`: `jar`

## Comandos habituales

Compilar:

```bash
./mvnw compile
```

Ejecutar la aplicación:

```bash
./mvnw spring-boot:run
```

Ejecutar tests:

```bash
./mvnw test
```

Generar el `.jar` ejecutable:

```bash
./mvnw package
```

En Windows (PowerShell / cmd) usar `mvnw.cmd` en lugar de `./mvnw`.

## Base de datos

El proyecto usa MySQL. Crea la base de datos ejecutando `scripts/create-database.sql` contra tu servidor MySQL (por ejemplo con `mysql -u root -p < scripts/create-database.sql`), y ajusta usuario/contraseña en `src/main/resources/application.properties` si no son `root`/`root`.

## Recursos adicionales

- [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1.1/maven-plugin)
- [Spring Web (Servlet)](https://docs.spring.io/spring-boot/4.1.1/reference/web/servlet.html)
- [Spring Data JPA](https://docs.spring.io/spring-boot/4.1.1/reference/data/sql.html#data.sql.jpa-and-spring-data)
- [Validation](https://docs.spring.io/spring-boot/4.1.1/reference/io/validation.html)
