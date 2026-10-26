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
    @Query("select m from Match m where exists (select p from MatchParticipant p "
            + "where p.match = m and p.user.userName = :username) order by m.createdAt desc")
    List<Match> findForParticipant(@Param("username") String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Match m where m.matchId = :id")
    Optional<Match> findForUpdate(@Param("id") long id);
}
