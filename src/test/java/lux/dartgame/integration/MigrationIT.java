package lux.dartgame.integration;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationIT extends AbstractIT {
    @Test
    void boardHitsMigrationPreservesExistingRoundsWithoutInventingHistoricalHits() throws Exception {
        String schema = "board_hits_upgrade_test";
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword());
             var sql = connection.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
            try {
                Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                        POSTGRES.getPassword()).defaultSchema(schema).target("11").load().migrate();
                sql.execute("SET search_path TO " + schema);
                sql.execute("INSERT INTO users (user_name,password,email,role_id) "
                        + "SELECT 'existing','hash','old@test',role_id FROM roles WHERE role='USER'");
                sql.execute("INSERT INTO matches (owner_id,gametype_id,status,created_at) "
                        + "SELECT user_id,gametype_id,'IN_PROGRESS',now() FROM users "
                        + "CROSS JOIN gametypes WHERE gametype='301'");
                sql.execute("INSERT INTO match_participants (match_id,user_id,position) "
                        + "SELECT match_id,user_id,0 FROM matches CROSS JOIN users");
                sql.execute("INSERT INTO legs (match_id,leg_number,status) "
                        + "SELECT match_id,1,'IN_PROGRESS' FROM matches");
                sql.execute("INSERT INTO leg_player_stats (leg_id,user_id,position,points,turn) "
                        + "SELECT leg_id,user_id,0,241,1 FROM legs CROSS JOIN users");
                String columns = "points,turn,triple20s,bullseyes,highest_score,highest_checkout,"
                        + "scoring_stats_available,darts_thrown,first_nine_points,first_nine_darts,"
                        + "score40,score60,score80,score100,score120,score140,score160,score180";
                String selected = java.util.Arrays.stream(columns.split(","))
                        .map(column -> "s." + column).collect(java.util.stream.Collectors.joining(","));
                sql.execute("INSERT INTO played_rounds(match_id,stat_id," + columns + ") "
                        + "SELECT l.match_id,s.stat_id," + selected
                        + " FROM leg_player_stats s JOIN legs l USING(leg_id)");
                var migration = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(), POSTGRES.getPassword()).defaultSchema(schema).load();
                assertThat(migration.migrate().migrationsExecuted).isEqualTo(2);
                assertThat(migration.migrate().migrationsExecuted).isZero();
                try (var rows = sql.executeQuery("SELECT starting_player_offset FROM matches")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isZero();
                }
                try (var rows = sql.executeQuery("SELECT points,turn FROM played_rounds")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(241);
                    assertThat(rows.getInt(2)).isEqualTo(1);
                }
                try (var rows = sql.executeQuery("SELECT COUNT(*) FROM round_board_hits")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isZero();
                }
                sql.execute("INSERT INTO round_board_hits(round_id,board_number,single_hits) "
                        + "SELECT round_id,20,1 FROM played_rounds");
                sql.execute("DELETE FROM played_rounds");
                try (var rows = sql.executeQuery("SELECT COUNT(*) FROM round_board_hits")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isZero();
                }
            } finally {
                sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    @Test
    void guestMigrationPreservesRegisteredHistoryAndEnforcesExclusiveIdentities() throws Exception {
        String schema = "guest_upgrade_test";
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword());
             var sql = connection.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
            try {
                Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                        POSTGRES.getPassword()).defaultSchema(schema).target("9").load().migrate();
                sql.execute("SET search_path TO " + schema);
                sql.execute("INSERT INTO users (user_name,password,email,role_id) "
                        + "SELECT 'existing','hash','old@test',role_id FROM roles WHERE role='USER'");
                sql.execute("INSERT INTO matches (owner_id,gametype_id,status,created_at,winner_id) "
                        + "SELECT user_id,gametype_id,'COMPLETED',now(),user_id FROM users "
                        + "CROSS JOIN gametypes WHERE gametype='301'");
                sql.execute("INSERT INTO match_participants (match_id,user_id,position) "
                        + "SELECT match_id,user_id,0 FROM matches CROSS JOIN users");
                sql.execute("INSERT INTO legs (match_id,leg_number,status,winner_id) "
                        + "SELECT match_id,1,'COMPLETED',user_id FROM matches CROSS JOIN users");
                sql.execute("INSERT INTO leg_player_stats (leg_id,user_id,position,points) "
                        + "SELECT leg_id,user_id,0,0 FROM legs CROSS JOIN users");
                Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                        POSTGRES.getPassword()).defaultSchema(schema).load().migrate();
                try (var rows = sql.executeQuery("SELECT m.winner_id,m.winner_guest_name,"
                        + "s.user_id,s.guest_name,s.points FROM matches m JOIN legs l USING(match_id) "
                        + "JOIN leg_player_stats s USING(leg_id)")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong(1)).isEqualTo(rows.getLong(3)).isPositive();
                    assertThat(rows.getString(2)).isNull();
                    assertThat(rows.getString(4)).isNull();
                    assertThat(rows.getInt(5)).isZero();
                }
                sql.execute("INSERT INTO match_participants (match_id,guest_name,position) "
                        + "SELECT match_id,'Guest',1 FROM matches");
                assertThatThrownBy(() -> sql.execute("INSERT INTO match_participants "
                        + "(match_id,position) SELECT match_id,2 FROM matches"))
                        .hasMessageContaining("participant_identity_check");
                assertThatThrownBy(() -> sql.execute("INSERT INTO match_participants "
                        + "(match_id,user_id,guest_name,position) SELECT match_id,user_id,'Other',2 "
                        + "FROM matches CROSS JOIN users"))
                        .hasMessageContaining("participant_identity_check");
                sql.execute("UPDATE matches SET winner_id=NULL,winner_guest_name='Guest'");
                sql.execute("UPDATE legs SET winner_id=NULL,winner_guest_name='Guest'");
            } finally {
                sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    @Test
    void singleOutMigrationAddsModesWithoutChangingExistingIds() throws Exception {
        String schema = "single_out_upgrade_test";
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword());
             var sql = connection.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
            try {
                Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                        POSTGRES.getPassword()).defaultSchema(schema).target("8")
                        .load().migrate();
                sql.execute("SET search_path TO " + schema);
                long existingId;
                try (var rows = sql.executeQuery("SELECT gametype_id FROM gametypes WHERE gametype='501'")) {
                    assertThat(rows.next()).isTrue();
                    existingId = rows.getLong(1);
                }
                var flyway = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(), POSTGRES.getPassword()).defaultSchema(schema)
                        .target("9").load();
                assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
                assertThat(flyway.migrate().migrationsExecuted).isZero();
                try (var rows = sql.executeQuery("SELECT gametype FROM gametypes ORDER BY gametype")) {
                    var modes = new java.util.ArrayList<String>();
                    while (rows.next()) {
                        modes.add(rows.getString(1));
                    }
                    assertThat(modes).containsExactly("301", "301 Single Out", "501", "501 Single Out");
                }
                try (var rows = sql.executeQuery("SELECT gametype_id FROM gametypes WHERE gametype='501'")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong(1)).isEqualTo(existingId);
                }
            } finally {
                sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

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
