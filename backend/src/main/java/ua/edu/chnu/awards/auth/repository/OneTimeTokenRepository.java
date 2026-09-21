package ua.edu.chnu.awards.auth.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ua.edu.chnu.awards.auth.entity.OneTimeToken;
import ua.edu.chnu.awards.auth.entity.TokenPurpose;

/**
 * Access to {@link OneTimeToken} rows.
 */
public interface OneTimeTokenRepository extends JpaRepository<OneTimeToken, Long> {

    Optional<OneTimeToken> findByTokenHashAndPurpose(String tokenHash, TokenPurpose purpose);

    /**
     * Marks a token used if, and only if, it is still unused and not expired.
     *
     * @param tokenHash hash of the presented token
     * @param purpose   expected purpose
     * @param now       the current moment
     * @return 1 when this call redeemed the token, 0 otherwise
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update OneTimeToken t set t.usedAt = :now
        where t.tokenHash = :tokenHash and t.purpose = :purpose and t.usedAt is null and t.expiresAt > :now
        """)
    int redeem(@Param("tokenHash") String tokenHash, @Param("purpose") TokenPurpose purpose,
               @Param("now") Instant now);

    /**
     * Marks every unused token of a user and purpose as used.
     *
     * @param userId  the owner
     * @param purpose the purpose
     * @param now     the current moment
     * @return number of tokens cancelled
     */
    @Modifying(flushAutomatically = true)
    @Query("""
        update OneTimeToken t set t.usedAt = :now
        where t.user.id = :userId and t.purpose = :purpose and t.usedAt is null
        """)
    int cancelUnused(@Param("userId") Long userId, @Param("purpose") TokenPurpose purpose,
                     @Param("now") Instant now);
}
