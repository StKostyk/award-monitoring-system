package ua.edu.chnu.awards.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ua.edu.chnu.awards.auth.entity.OneTimeToken;
import ua.edu.chnu.awards.auth.entity.TokenPurpose;

/**
 * Access to {@link OneTimeToken} rows.
 */
public interface OneTimeTokenRepository extends JpaRepository<OneTimeToken, Long> {

    Optional<OneTimeToken> findByTokenHashAndPurpose(String tokenHash, TokenPurpose purpose);
}
