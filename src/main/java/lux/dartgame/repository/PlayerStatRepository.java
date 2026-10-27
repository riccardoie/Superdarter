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
}
