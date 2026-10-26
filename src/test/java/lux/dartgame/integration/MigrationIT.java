package lux.dartgame.integration;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationIT extends AbstractIT {
    @Test
    void firstToMigrationPreservesExistingSingleLegMatches() throws Exception {
        String schema = "first_to_upgrade_test";
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword());
             var sql = connection.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
            try {
                Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                        POSTGRES.getPassword()).defaultSchema(schema).target("4")
                        .load().migrate();
                sql.execute("INSERT INTO " + schema + ".users "
                        + "(user_name,password,email,role_id) SELECT 'existing','hash','old@test',"
                        + "role_id FROM " + schema + ".roles WHERE role='USER'");
                sql.execute("INSERT INTO " + schema + ".matches "
                        + "(owner_id,gametype_id,status,created_at) SELECT u.user_id,g.gametype_id,"
                        + "'PENDING',now() FROM " + schema + ".users u CROSS JOIN "
                        + schema + ".gametypes g WHERE g.gametype='501'");
                Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                        POSTGRES.getPassword()).defaultSchema(schema).load().migrate();
                try (var rows = sql.executeQuery("SELECT legs_to_win,status FROM "
                        + schema + ".matches")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(1);
                    assertThat(rows.getString(2)).isEqualTo("PENDING");
                    assertThat(rows.next()).isFalse();
                }
            } finally {
                sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    @Test
    void refusesToDiscardExistingSessionHistory() throws Exception {
        String schema = "legacy_guard_test";
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword());
             var sql = connection.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
            try {
                Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                        POSTGRES.getPassword()).defaultSchema(schema).target("3")
                        .load().migrate();
                sql.execute("INSERT INTO " + schema + ".users "
                        + "(user_name,password,email,role_id) SELECT 'legacy','hash','test@test',"
                        + "role_id FROM " + schema + ".roles WHERE role='USER'");
                sql.execute("INSERT INTO " + schema + ".sessions (is_active,owner_id) "
                        + "SELECT true,user_id FROM " + schema + ".users");
                var flyway = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(), POSTGRES.getPassword()).defaultSchema(schema).load();
                assertThatThrownBy(flyway::migrate).hasStackTraceContaining("fresh database");
                try (var rows = sql.executeQuery("SELECT COUNT(*) FROM " + schema + ".sessions")) {
                    rows.next();
                    assertThat(rows.getInt(1)).isEqualTo(1);
                }
            } finally {
                sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}
