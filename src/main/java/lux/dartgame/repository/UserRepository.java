package lux.dartgame.repository;

import lux.dartgame.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface UserRepository extends JpaRepository<User, Long> {
    // Lock participant rows in ID order before aggregating across different matches.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :id")
    Optional<User> findForUpdate(@Param("id") long id);
    Optional<User> findByUserName(String username);

    // Field is called userName, to follow convention explicitly defined query
    @Query("SELECT u.userName FROM User u WHERE u.userId = :id")
    Optional<String> findUserNameById(@Param("id") Long id);

    boolean existsByUserName(String username);

    boolean existsByEmail(String email);

    @Query("SELECT u.role.role FROM User u WHERE u.userName = :username")
    Optional<String> findRoleByUserName(@Param("username") String username);

    @Query(value = "SELECT u.user_name FROM users u WHERE u.user_name <> :actor "
            + "AND strpos(lower(u.user_name), lower(:query)) > 0 "
            + "AND NOT EXISTS (SELECT 1 FROM friend_requests f JOIN users a "
            + "ON a.user_name = :actor WHERE "
            + "(f.sender_id = a.user_id AND f.recipient_id = u.user_id) OR "
            + "(f.recipient_id = a.user_id AND f.sender_id = u.user_id)) "
            + "ORDER BY CASE WHEN lower(u.user_name) = lower(:query) THEN 0 "
            + "WHEN strpos(lower(u.user_name), lower(:query)) = 1 THEN 1 ELSE 2 END, "
            + "length(u.user_name), lower(u.user_name), u.user_name LIMIT :limit",
            nativeQuery = true)
    List<String> suggestFriends(@Param("actor") String actor, @Param("query") String query,
                               @Param("limit") int limit);
}
