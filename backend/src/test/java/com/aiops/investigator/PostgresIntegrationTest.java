package com.aiops.investigator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.TestcontainersConfiguration;

/**
 * Starts one PostgreSQL container for the backend tests.
 *
 * <p>The container is started once for the whole test JVM. Spring caches the application context,
 * including the JDBC URL, and reuses it for every test class. A JUnit container extension would
 * stop that database after the first class and start another on a new port, and the cached URL
 * would point at a closed port. Ryuk, the Testcontainers cleanup process, removes the container
 * when the JVM exits. {@code @ActiveProfiles("test")} loads {@code application-test.yml} on top of
 * the main settings and turns the worker timer off. {@code @DynamicPropertySource} copies the
 * address into Spring before the application starts. Flyway then creates the schema in that empty
 * database.
 *
 * <p>The JDBC host is {@code 127.0.0.1}. On this Mac {@code localhost} resolves to IPv6 {@code ::1}
 * first, and that address refuses the published port. Rancher Desktop also opens the IPv4 port
 * about a second after Docker reports the container as started, so the test waits until {@code
 * 127.0.0.1} accepts a connection before Spring tries to use it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

  static {
    preferRancherDesktopSocket();
    POSTGRES.start();
  }

  /**
   * Points Testcontainers at Rancher Desktop when the usual Docker socket is absent.
   *
   * <p>The Docker command-line tool reads the selected context and finds {@code ~/.rd/docker.sock}.
   * Testcontainers does not. It looks at {@code DOCKER_HOST}, then {@code
   * ~/.testcontainers.properties}, then {@code /var/run/docker.sock}. This method writes the
   * Rancher socket into the in-memory property map only. It does not create or change a file in the
   * home directory. An explicit {@code DOCKER_HOST} or an existing {@code /var/run/docker.sock} is
   * left alone.
   */
  private static void preferRancherDesktopSocket() {
    if (System.getenv("DOCKER_HOST") != null) {
      return;
    }
    if (Files.exists(Path.of("/var/run/docker.sock"))) {
      return;
    }
    Path rancherSocket = Path.of(System.getProperty("user.home"), ".rd", "docker.sock");
    if (!Files.exists(rancherSocket)) {
      return;
    }
    // The path is absolute, so this is unix:///Users/.../docker.sock. Path.toUri() would say
    // file:// instead, and the Docker client would reject that.
    String dockerHost = "unix://" + rancherSocket;
    TestcontainersConfiguration.getInstance()
        .getUserProperties()
        .putIfAbsent("docker.host", dockerHost);
  }

  /** Copies the container address into Spring before the application opens a connection. */
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", PostgresIntegrationTest::jdbcUrlWhenThePortAccepts);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  /**
   * Builds the JDBC URL and blocks until the published port accepts TCP.
   *
   * <p>Testcontainers decides the container is ready from PostgreSQL's log line. Hikari then asks
   * for a connection once. On Rancher Desktop that first attempt lands before the port forwarder is
   * listening, and the pool stops. Waiting here makes the first real connection succeed.
   */
  private static String jdbcUrlWhenThePortAccepts() {
    int port = POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT);
    waitUntilAccepted(port);
    return POSTGRES.getJdbcUrl().replace("://localhost:", "://127.0.0.1:");
  }

  /**
   * Polls {@code 127.0.0.1} until a TCP connection succeeds or 30 seconds pass.
   *
   * @param port the host port Docker published for PostgreSQL
   */
  private static void waitUntilAccepted(int port) {
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (System.nanoTime() < deadline) {
      try (Socket socket = new Socket()) {
        socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
        return;
      } catch (IOException ignored) {
        try {
          Thread.sleep(200);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
    throw new IllegalStateException("PostgreSQL did not accept connections on 127.0.0.1:" + port);
  }
}
