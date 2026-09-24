package ua.edu.chnu.awards.user.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ua.edu.chnu.awards.user.entity.User;

/**
 * Access to {@link User} rows.
 */
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailAddressIgnoreCase(String emailAddress);

    boolean existsByEmailAddressIgnoreCase(String emailAddress);

    /**
     * The user with the row locked until the transaction ends, so two callers changing the same person's roles
     * are serialised and the check for an assignment that already covers the period cannot be overtaken.
     *
     * @param id the user
     * @return the user
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
