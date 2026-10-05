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
     * Counts the tokens of a user and purpose that are neither used nor expired.
     *
     * @param userId  the owner
     * @param purpose the purpose
     * @param now     the current moment
     * @return number of tokens that can still be redeemed
     */
    @Query("""
        select count(t) from OneTimeToken t
        where t.user.id = :userId and t.purpose = :purpose and t.usedAt is null and t.expiresAt > :now
        """)
    long countUsable(@Param("userId") Long userId, @Param("purpose") TokenPurpose purpose,
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

    /**
     * Marks every unused token of a user and purpose that carries no address as used.
     *
     * @param userId  the owner
     * @param purpose the purpose
     * @param now     the current moment
     * @return number of tokens cancelled
     */
    @Modifying(flushAutomatically = true)
    @Query("""
        update OneTimeToken t set t.usedAt = :now
        where t.user.id = :userId and t.purpose = :purpose and t.usedAt is null and t.newEmailAddress is null
        """)
    int cancelUnusedWithoutAddress(@Param("userId") Long userId, @Param("purpose") TokenPurpose purpose,
                                   @Param("now") Instant now);

    /**
     * Gives every usable token of a user and purpose that carries no address the given one.
     *
     * @param userId  the owner
     * @param purpose the purpose
     * @param address the address to carry
     * @param now     the current moment
     * @return number of tokens changed
     */
    @Modifying(flushAutomatically = true)
    @Query("""
        update OneTimeToken t set t.newEmailAddress = :address
        where t.user.id = :userId and t.purpose = :purpose and t.usedAt is null and t.expiresAt > :now
          and t.newEmailAddress is null
        """)
    int bindAddress(@Param("userId") Long userId, @Param("purpose") TokenPurpose purpose,
                    @Param("address") String address, @Param("now") Instant now);
}
