package lux.dartgame.integration;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import lux.dartgame.dto.LoginRequest;
import lux.dartgame.dto.RegisterRequest;
import lux.dartgame.dto.TokenResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for tests that exercise the real HTTP stack, the real security
 * filter chain and a real PostgreSQL instance. Testcontainers starts one
 * database per test JVM, so Flyway applies the real migrations and Hibernate
 * validates against the real schema.
 * <p>
 * Every test starts from and returns to the same database state: all
 * application rows are removed and their identity sequences are reset, leaving
 * only test-owned reference data (roles and game modes). Tests therefore
 * do not depend on each other and may run in any order.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractIT {

    protected static final String AUTH_URL = "/api/auth";
    protected static final String SESSION_URL = "/api/session";
    protected static final String GAMEMODE_URL = "/api/gamemode";
    protected static final String PLAYER_STATS_URL = "/api/playerStats";

    protected static final String PASSWORD = "correct-horse-battery-staple";
    protected static final String EMAIL_DOMAIN = "@example.test";
    protected static final String TARGET_301 = "301";
    protected static final String TARGET_501 = "501";

    /**
     * Empties every table the application owns and restarts its sequences.
     * Roles and game modes survive: they are reference data, never written by
     * a test, and the endpoints under test need them to exist.
     */
    private static final String RESET_STATEMENT =
            "TRUNCATE TABLE gamestats, games, session_mm_users, sessions, "
                    + "playerstats, users RESTART IDENTITY CASCADE";

    private static final String SECRET_RAW = "lux-integration-test-secret-key-32bytes!";

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    static {
        // RequiredEnvironmentValidator runs while the environment is prepared,
        // which happens before any test-owned property source is registered.
        // Providing the four mandatory variables as JVM system properties while
        // this class loads satisfies it.
        System.setProperty("POSTGRES_DB", POSTGRES.getDatabaseName());
        System.setProperty("POSTGRES_USER", POSTGRES.getUsername());
        System.setProperty("POSTGRES_PASSWORD", POSTGRES.getPassword());
        System.setProperty("JWT_SECRET", Base64.getEncoder().encodeToString(
                SECRET_RAW.getBytes(StandardCharsets.UTF_8)));

        POSTGRES.start();
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void datasourceProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /**
     * Runs before and after every test, so each test both starts from a clean
     * database and leaves it clean for whatever runs next.
     */
    @BeforeEach
    @AfterEach
    void resetDatabase() {
        jdbc.execute(RESET_STATEMENT);
        jdbc.execute("INSERT INTO roles (role) VALUES ('USER'), ('ADMIN') ON CONFLICT DO NOTHING");
        jdbc.execute("INSERT INTO gametypes (gametype) VALUES "
                + "('501'), ('301'), ('Cricket'), ('Killer'), ('Around the Clock') ON CONFLICT DO NOTHING");
    }

    protected TestRestTemplate rest() {
        return rest;
    }

    protected JdbcTemplate jdbc() {
        return jdbc;
    }

    /**
     * Registers a user through the public endpoint and returns the bearer
     * token that endpoint issued.
     */
    protected String register(final String username) {
        TokenResponse response = rest.postForObject(AUTH_URL + "/register",
                new RegisterRequest(username, PASSWORD, username + EMAIL_DOMAIN),
                TokenResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.token()).isNotBlank();
        assertThat(response.type()).isEqualTo("Bearer");
        return response.token();
    }

    protected String login(final String username, final String password) {
        TokenResponse response = rest.postForObject(AUTH_URL + "/login",
                new LoginRequest(username, password), TokenResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.token()).isNotBlank();
        return response.token();
    }

    protected HttpHeaders bearer(final String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    protected <T> HttpEntity<T> authed(final String token, final T body) {
        return new HttpEntity<>(body, bearer(token));
    }

    protected int countRows(final String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
