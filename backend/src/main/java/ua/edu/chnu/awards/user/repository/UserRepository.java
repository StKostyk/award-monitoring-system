package ua.edu.chnu.awards.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ua.edu.chnu.awards.user.entity.User;

/**
 * Access to {@link User} rows.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailAddressIgnoreCase(String emailAddress);

    boolean existsByEmailAddressIgnoreCase(String emailAddress);
}
