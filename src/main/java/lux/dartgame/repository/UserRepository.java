package lux.dartgame.repository;

import lux.dartgame.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
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
}
