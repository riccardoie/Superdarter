package lux.dartgame.integration;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationIT extends AbstractIT {
    @Test
    void detailedStatsMigrationPreservesHistoryAndMarksPlayedLegsUnavailable() throws Exception {
        String schema = "detailed_stats_upgrade_test";
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword());
             var sql = connection.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
            try {
                Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                        POSTGRES.getPassword()).defaultSchema(schema).target("5")
                        .load().migrate();
                sql.execute("SET search_path TO " + schema);
                sql.execute("INSERT INTO users (user_name,password,email,role_id) "
                        + "SELECT 'existing','hash','old@test',role_id FROM roles WHERE role='USER'");
                sql.execute("INSERT INTO matches (owner_id,gametype_id,status,created_at) "
                        + "SELECT user_id,gametype_id,'IN_PROGRESS',now() FROM users "
                        + "CROSS JOIN gametypes WHERE gametype='501'");
                sql.execute("INSERT INTO legs (match_id,leg_number,status) "
                        + "SELECT match_id,n,'IN_PROGRESS' FROM matches CROSS JOIN "
                        + "generate_series(1,2) AS n");
                sql.execute("INSERT INTO leg_player_stats (leg_id,user_id,position,points,turn) "
                        + "SELECT leg_id,user_id,0,CASE WHEN leg_number=1 THEN 321 ELSE 501 END,"
                        + "CASE WHEN leg_number=1 THEN 1 ELSE 0 END FROM legs CROSS JOIN users");
                Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                        POSTGRES.getPassword()).defaultSchema(schema).load().migrate();
                try (var rows = sql.executeQuery("SELECT points,scoring_stats_available "
                        + "FROM leg_player_stats ORDER BY points")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(321);
                    assertThat(rows.getBoolean(2)).isFalse();
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(501);
                    assertThat(rows.getBoolean(2)).isTrue();
                }
                try (var rows = sql.executeQuery("SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema='" + schema + "' AND column_name IN "
                        + "('checkout_accuracy','avg_checkout_accuracy')")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isZero();
                }
                sql.execute("INSERT INTO legs (match_id,leg_number,status) "
                        + "SELECT match_id,3,'PENDING' FROM matches");
                sql.execute("INSERT INTO leg_player_stats (leg_id,user_id,position,points) "
                        + "SELECT leg_id,user_id,0,501 FROM legs CROSS JOIN users "
                        + "WHERE leg_number=3");
                try (var rows = sql.executeQuery("SELECT scoring_stats_available,darts_thrown "
                        + "FROM leg_player_stats s JOIN legs l USING(leg_id) WHERE leg_number=3")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getBoolean(1)).isTrue();
                    assertThat(rows.getInt(2)).isZero();
                }
            } finally {
                sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

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
