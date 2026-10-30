package lux.dartgame.repository;

import lux.dartgame.model.PlayerStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlayerStatRepository extends JpaRepository<PlayerStat, Long> {
    List<PlayerStat> findByPlayerUserNameIn(List<String> usernames);

    @Query("select case when coalesce(sum(case when s.scoringStatsAvailable = false "
            + "then 1 else 0 end), 0) > 0 then null else coalesce(sum(s.dartsThrown), 0) end "
            + "from LegPlayerStat s where s.user.userName = :username "
            + "and s.leg.status = lux.dartgame.model.MatchStatus.COMPLETED")
    Long findCompletedDartsThrown(@Param("username") String username);

    interface BoardHitTotal {
        Integer getBoardNumber();
        Long getSingleHits();
        Long getDoubleHits();
        Long getTripleHits();
    }

    @Query(value = "SELECT h.board_number AS boardNumber, SUM(h.single_hits) AS singleHits, "
            + "SUM(h.double_hits) AS doubleHits, SUM(h.triple_hits) AS tripleHits "
            + "FROM round_board_hits h JOIN played_rounds r ON r.round_id = h.round_id "
            + "JOIN leg_player_stats s ON s.stat_id = r.stat_id "
            + "JOIN users u ON u.user_id = s.user_id WHERE u.user_name = :username "
            + "GROUP BY h.board_number", nativeQuery = true)
    List<BoardHitTotal> findBoardHits(@Param("username") String username);
}
