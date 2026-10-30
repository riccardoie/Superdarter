package lux.dartgame.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import lux.dartgame.model.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchRepository extends JpaRepository<Match, Long> {
    interface BoardHitTotal {
        Long getStatId();
        Integer getBoardNumber();
        Long getSingleHits();
        Long getDoubleHits();
        Long getTripleHits();
    }

    @Query(value = "SELECT r.stat_id AS statId, h.board_number AS boardNumber, "
            + "SUM(h.single_hits) AS singleHits, SUM(h.double_hits) AS doubleHits, "
            + "SUM(h.triple_hits) AS tripleHits FROM round_board_hits h "
            + "JOIN played_rounds r ON r.round_id = h.round_id WHERE r.match_id = :id "
            + "GROUP BY r.stat_id, h.board_number", nativeQuery = true)
    List<BoardHitTotal> findBoardHits(@Param("id") long id);

    @Query("select m from Match m where exists (select p from MatchParticipant p "
            + "where p.match = m and p.user.userName = :username) order by m.createdAt desc")
    List<Match> findForParticipant(@Param("username") String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Match m where m.matchId = :id")
    Optional<Match> findForUpdate(@Param("id") long id);
}
