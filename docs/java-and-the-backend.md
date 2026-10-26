# Java, and how this backend is built

This guide is for a reader who is new to this backend. It starts with how a Java program is compiled and started, then walks the files in `backend/` in the order Maven and Spring actually use them.

The sibling guide is [`python-and-the-ai-service.md`](python-and-the-ai-service.md). The two processes are separate programs. This one is the system of record. Phase 0 of it does one job: start an HTTP server and answer `GET /api/platform/status` with a fixed JSON document. It does not open a database and it does not call the AI service.

The code below matches the repository. When a later phase changes the backend, update this guide in the same change.

## 1. Java compiles, then a virtual machine runs the result

A `.java` file is source. Nothing runs it directly. The compiler, `javac`, reads the source and writes a `.class` file of bytecode. The Java Virtual Machine (JVM) loads those `.class` files and runs them.

That is the opposite of the Python service. Importing a Python module runs the file. A Java `import` does not run anything. It only tells the compiler "when I write `SpringApplication`, I mean the class with that full name." The class runs later, when some method calls it.

Maven is the program that runs the compiler, downloads libraries, and runs the tests. You do not call `javac` yourself in this repository.

```text
.java source
    →  javac, invoked by Maven
        →  .class files in backend/target/
            →  JVM starts at InvestigatorApplication.main
                →  Spring Boot builds the application and opens port 8080
```

`backend/target/` is that build output. It is gitignored. Deleting it is safe. The next Maven run creates it again.

Names are spelled out more than in Python:

| Piece | Example in this project | Meaning |
|---|---|---|
| Package | `com.aiops.investigator` | The class's folder name, written with dots. |
| Class | `InvestigatorApplication` | One named type. The file name must match the public class. |
| Method | `main`, `status` | A function that belongs to a class. |
| Object | `new PlatformStatus(...)` | One value created from a class. |

A class is the blueprint. `new PlatformStatus(...)` builds one object from that blueprint. The compiler checks types before the program starts. If `status()` promised to return a `PlatformStatus` and the code returned a number, Maven's compile step would fail. There is no separate mypy step. The compiler is that check.

## 2. How the source folders map to packages

Maven's convention is fixed. Source for the running program is under `src/main`. Source that exists only for tests is under `src/test`. The directory under `java/` must match the package line in the file.

```text
backend/
├── pom.xml                         # Maven project: dependencies, Java version, plugins
├── mvnw                            # script that downloads and runs Maven 3.10.0
├── .mvn/wrapper/                   # where that Maven download is configured
└── src/
    ├── main/
    │   ├── java/com/aiops/investigator/
    │   │   ├── package-info.java
    │   │   ├── InvestigatorApplication.java
    │   │   └── api/
    │   │       ├── package-info.java
    │   │       ├── PlatformStatus.java
    │   │       └── PlatformStatusController.java
    │   └── resources/
    │       └── application.yml     # settings read at startup
    └── test/java/com/aiops/investigator/
        └── InvestigatorApplicationTests.java
```

`package com.aiops.investigator.api;` means "this file lives in `com/aiops/investigator/api/`." The dots are folder separators, the same idea as Python's `app.main`, but Java checks that the folders and the package line agree at compile time.

`package-info.java` is not a runnable class. It holds the package's documentation comment. Spring does not load it as a bean.

## 3. Maven, and the one file that decides the classpath

The classpath is the list of classes and libraries the compiler and the JVM are allowed to see. Maven builds that list from `pom.xml`.

A dependency is written as coordinates: group, artifact, version.

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>4.1.2</version>
  <relativePath/>
</parent>
```

The parent is Spring Boot's bill of materials. A starter dependency in this file can omit its version because the parent already chose one that works with Boot 4.1.2. `<relativePath/>` means "do not look for that parent in a nearby folder; download it."

This project names itself `com.aiops:investigator:0.0.1-SNAPSHOT`. `SNAPSHOT` means the version is still moving. It is not a release.

`<java.version>21</java.version>` tells the parent to compile for Java 21. The JDK on the machine has to be 21 or newer to run that compile.

The runtime libraries are:

| Artifact | Why it is here in Phase 0 |
|---|---|
| `spring-boot-starter-webmvc` | Embedded server and Spring MVC, which turns an HTTP request into a method call. Boot 4 uses this name. The older name `spring-boot-starter-web` is not what this file declares. |
| `spring-boot-starter-actuator` | `/actuator/health` and `/actuator/info`. |
| `spring-boot-starter-validation` | Bean validation. No route validates input yet. The library is here so later request objects can. |
| `springdoc-openapi-starter-webmvc-ui` | `/api/openapi` and the Swagger UI page. Its version is `3.2.0` because springdoc is not managed by the Boot parent. |

There is no PostgreSQL driver and no Spring Data dependency. The process can start with no database because nothing on the classpath tries to open one.

Dependencies with `<scope>test</scope>` are visible to the tests and are not packaged into the running application. `spring-boot-starter-webmvc-test` is what provides `MockMvc`.

`./mvnw` is a script checked into the repository. The first run downloads Maven 3.10.0 from the URL in `.mvn/wrapper/maven-wrapper.properties`. After that, `./mvnw` and a system Maven of another version are not the same tool. Use the wrapper.

Commands, from `backend/`:

| Command | What runs |
|---|---|
| `./mvnw -B verify` | Compile, run tests, then Spotless check. This is the check used before a change is called done. `-B` means batch: no prompts. |
| `./mvnw -B spotless:apply` | Rewrite Java layout. The check only reports differences. |
| `./mvnw spring-boot:run` | Start the server. |
| `./mvnw -B -Dtest=InvestigatorApplicationTests test` | Run one test class. |

`verify` is a Maven lifecycle phase. It runs earlier phases in order: compile main code, compile tests, run tests, then the extra checks bound to `verify`. Spotless is bound there:

```xml
<execution>
  <goals>
    <goal>check</goal>
  </goals>
  <phase>verify</phase>
</execution>
```

Spotless with google-java-format is the formatter, in the same role Ruff's formatter has for the Python service. The pinned formatter is 1.36.1. Version 1.37.0 crashes inside Spotless 3.10.4 on this JDK, which is why the pom does not use the newest number.

`spring-boot:run` is a plugin goal, not a lifecycle phase. It compiles if needed and starts `main`.

## 4. Settings are data, read once at startup

`src/main/resources/application.yml` is copied into the built application. Spring Boot reads it while starting and sets its own objects from the keys. YAML indentation is the structure. A comment starts with `#`.

```yaml
server:
  address: 127.0.0.1
  port: 8080
```

`server.address` is the bind address of the listening socket. `127.0.0.1` is loopback, so another machine cannot open the port. `8080` is the port. Nothing in Java makes 8080 special. It is this file.

```yaml
spring:
  mvc:
    problemdetails:
      enabled: true
```

When a later request fails validation, the error body is an RFC 9457 problem document instead of an HTML error page. The Phase 0 status route does not fail validation, so this setting is idle until those routes exist.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
```

Actuator has more endpoints than health and info. Only those two are exposed on the web. They are on the same port as the application because this file does not set a separate management port. The test calls `GET /actuator/health` and expects JSON `{"status":"UP", ...}`.

```yaml
logging:
  structured:
    format:
      console: logstash
```

Log lines on the console are JSON objects in Logstash's shape, not plain sentences. They describe this process. They are not the investigation evidence.

`springdoc.api-docs.path` is `/api/openapi`. `springdoc.swagger-ui.path` is `/swagger-ui.html`. Those pages are generated from the controller annotations. Nobody wrote an OpenAPI file by hand.

## 5. What the running process is

```text
./mvnw spring-boot:run
  →  Maven builds the classpath from pom.xml
      →  JVM calls InvestigatorApplication.main
          →  SpringApplication.run creates the application context
              →  component scan finds PlatformStatusController
                  →  embedded server binds 127.0.0.1:8080
```

The application context is the object that holds the application's beans. A bean is an object Spring constructs and keeps. `PlatformStatusController` is a bean. `PlatformStatus` is not. The controller creates a new `PlatformStatus` with `new` on each request and then throws that object away after Jackson has written the JSON.

## 6. The entry class

`InvestigatorApplication.java`:

```java
package com.aiops.investigator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class InvestigatorApplication {

  public static void main(String[] args) {
    SpringApplication.run(InvestigatorApplication.class, args);
  }
}
```

`public static void main(String[] args)` is the signature the JVM looks for. `public` means any caller may use it. `static` means the method belongs to the class, not to one object, so the JVM can call it before any `InvestigatorApplication` object exists. `void` means it returns nothing. `args` is the command-line arguments. Spring Boot reads those too, so `--server.port=8081` can override the YAML for one run.

`SpringApplication.run(InvestigatorApplication.class, args)` is the start. `.class` is a reference to the class itself, not an instance. Spring uses that class as the starting point for the scan and for auto-configuration.

`@SpringBootApplication` is an annotation. An annotation is metadata attached to a class or method. It does not run code by sitting there. Spring reads it. This one combines three instructions:

- This class is a configuration class.
- Turn on auto-configuration: look at the classpath and create the usual server, JSON, and actuator beans.
- Scan `com.aiops.investigator` and its child packages for beans.

Auto-configuration is why adding `spring-boot-starter-webmvc` to the pom is enough to get an HTTP server. The starter puts the server library on the classpath. Boot's auto-configuration sees it and creates the server. Remove the starter, and the next build has no web server, without a change to `main`.

The scan boundary is the package of this class. `PlatformStatusController` is found because `com.aiops.investigator.api` is inside that tree. A class you put in `com.aiops.other` would compile and then be ignored by this scan.

## 7. The JSON type

`PlatformStatus.java`:

```java
public record PlatformStatus(String service, String environmentLabel, String phase) {}
```

A `record` is a class whose fields are fixed at construction. The compiler writes the constructor, the accessors `service()`, `environmentLabel()`, and `phase()`, and `equals` and `hashCode`. There is no setter. After `new PlatformStatus("investigator-backend", "synthetic-test", "bootstrap")`, those three strings cannot be replaced on that object.

The record is not a Spring bean. Nothing in the file asks Spring to create it. The controller calls `new`.

Jackson, the JSON library Boot puts on the classpath, turns the record into one JSON object. The test locks the names:

```json
{
  "service": "investigator-backend",
  "environmentLabel": "synthetic-test",
  "phase": "bootstrap"
}
```

The keys are the record component names. `environmentLabel` stays camel case. The Python health route uses `environment_label`. Those are two different contracts. A caller cannot assume one service's JSON matches the other's because the words look similar.

## 8. The route

`PlatformStatusController.java`:

```java
@RestController
@RequestMapping("/api/platform")
public class PlatformStatusController {

  @GetMapping("/status")
  public PlatformStatus status() {
    return new PlatformStatus("investigator-backend", "synthetic-test", "bootstrap");
  }
}
```

`@RestController` marks the class as a bean whose methods return the HTTP body, not the name of an HTML template. Spring MVC creates one `PlatformStatusController` object at startup and keeps it.

`@RequestMapping("/api/platform")` is the path prefix for every method in the class. `@GetMapping("/status")` adds `GET` and the suffix `/status`. The registered route is `GET /api/platform/status`. The strings are URL paths, not files.

`GET` is the method a client uses when it only reads. `status()` takes no arguments because the answer does not depend on the request. Every successful call builds the same three strings.

Spring MVC's path from socket to JSON:

1. The operating system delivers the TCP connection to the embedded server, which owns `127.0.0.1:8080`.
2. The server parses the HTTP request.
3. Spring MVC finds the method mapped to `GET /api/platform/status`.
4. It calls `status()` on the one controller bean.
5. Jackson serializes the returned record.
6. The server writes `200` and `Content-Type: application/json`, then the body.

A path with no mapping, such as `GET /api/incidents`, does not reach `status()`. Spring MVC returns 404. Phase 0 has no incident routes.

`/swagger-ui.html` and `/api/openapi` are extra routes from springdoc. They describe `status()` because of the annotations. They are not written in this class.

## 9. The test

`InvestigatorApplicationTests.java` is under `src/test`, so it is not in the running jar.

```java
@SpringBootTest
@AutoConfigureMockMvc
class InvestigatorApplicationTests {

  @Autowired private MockMvc mockMvc;

  @Test
  void contextLoads() {}

  @Test
  void statusSaysTheEnvironmentIsSynthetic() throws Exception {
    mockMvc
        .perform(get("/api/platform/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.service").value("investigator-backend"))
        .andExpect(jsonPath("$.environmentLabel").value("synthetic-test"))
        .andExpect(jsonPath("$.phase").value("bootstrap"));
  }
}
```

JUnit 5 is the test runner. `@Test` marks a method as one test. The class has no `public` modifier. JUnit can still run it. Only the entry class has to be `public` so the JVM can launch it.

`@SpringBootTest` starts the same application context as the server, including the controller bean. It does not need PostgreSQL, because this application does not open a connection.

`@AutoConfigureMockMvc` adds a `MockMvc` bean. `MockMvc` calls the HTTP handlers in memory. No socket opens, so the test does not need port 8080 to be free. That is the same role `TestClient` has in the Python service.

`@Autowired` tells Spring to put that `MockMvc` into the field before the tests run. The field is assigned by the framework, not by a constructor in this class.

`contextLoads` has an empty body on purpose. The useful check is that building the context did not throw. A broken bean fails this test before the method body matters.

`perform(get(...))` builds a fake GET request. `andExpect` asserts on the result. `status().isOk()` is HTTP 200. `jsonPath("$.environmentLabel")` reads one JSON field. `$` is the root object.

`throws Exception` is on the method because `perform` declares a checked exception. A checked exception must be caught or declared. The test declares it and lets JUnit treat a throw as a failure.

The third test calls `GET /actuator/health` and expects `$.status` to be `UP`. That route comes from Actuator, not from `PlatformStatusController`.

## 10. Nuances that matter in this repository

**An import does not run the class.** In the Python service, importing `app.main` calls `create_app()`. In this backend, importing a class only affects compilation. Objects appear when Spring creates beans at startup, or when `status()` executes `new`.

**Annotations are not function calls.** `@GetMapping("/status")` does not register the route at the moment the compiler reads it. Spring reads the annotation when it builds the context and registers the route then.

**One controller bean, one new record per request.** Spring does not call `status()` at startup. A request does. The record is not stored.

**Classpath decides features.** Web, actuator, validation, and OpenAPI are present because of `pom.xml`, not because `main` names them. A library that is absent cannot be turned on from YAML.

**Test libraries stay out of the server.** A `test`-scoped dependency can make `./mvnw verify` pass and still be invisible to `spring-boot:run`.

**The scan root is the application package.** A new `@RestController` has to live under `com.aiops.investigator` or it will not be registered, even if the file compiles.

**JSON names are the Java names.** `environmentLabel` is not converted to `environment_label`. The Python health body uses the snake-case name. Do not mix the two documents.

**Bind address is configuration.** The Java source does not mention `127.0.0.1`. `application.yml` does. A test with `MockMvc` never opens that socket. `spring-boot:run` does.

**`verify` is stricter than `spring-boot:run`.** The server goal does not run the tests or the Spotless check. A class can start and still fail `verify` because the formatter disagrees.

**Problem details are enabled and unused.** No Phase 0 route returns a validation error. The setting is there so the later incident API does not have to rediscover it.

**Logs are not evidence.** Structured console logs describe the backend process. They are not the investigation record the product will store in PostgreSQL.

## 11. What a later phase adds, and where it will go

Incident storage is not in these classes. When it arrives, the shape should stay the same kind of Java:

- Types under `com.aiops.investigator`, so the existing scan finds them. HTTP handlers stay in the `api` package. Database types and connector types get their own packages beside `api`.
- A Flyway migration under `src/main/resources/db/migration/`, plus the PostgreSQL and Spring Data dependencies in `pom.xml`. Those dependencies are what will make startup open a database. They are intentionally absent now.
- `POST /api/incidents/investigate` returning `202` after the row commits. The idempotency header and the retry routes are already decided in `docs/adr/0007-accepted-execution-notes.md`. The handler should return a type the way `status()` returns a `PlatformStatus`, and Jackson will serialize it.
- A client bean that calls the Python service. The backend remains usable when that call fails. Phase 0 does not contain that client, which is why this process never reads `OPENAI_API_KEY`.
