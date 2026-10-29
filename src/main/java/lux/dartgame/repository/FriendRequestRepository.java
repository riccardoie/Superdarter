package lux.dartgame.repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import lux.dartgame.model.FriendRequest;
import lux.dartgame.model.FriendRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {
    @Query("select f from FriendRequest f where "
            + "(f.sender.userId = :first and f.recipient.userId = :second) or "
            + "(f.sender.userId = :second and f.recipient.userId = :first)")
    Optional<FriendRequest> findBetween(@Param("first") long first, @Param("second") long second);

    @Query("select f from FriendRequest f join fetch f.sender join fetch f.recipient "
            + "where f.status = :status and "
            + "(f.sender.userName = :actor or f.recipient.userName = :actor) "
            + "order by f.createdAt desc, f.requestId desc")
    List<FriendRequest> findForUser(@Param("actor") String actor,
                                  @Param("status") FriendRequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FriendRequest f where f.requestId = :id")
    Optional<FriendRequest> findForUpdate(@Param("id") long id);
}
