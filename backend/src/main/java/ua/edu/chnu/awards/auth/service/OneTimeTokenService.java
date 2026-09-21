package ua.edu.chnu.awards.auth.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.auth.entity.OneTimeToken;
import ua.edu.chnu.awards.auth.entity.TokenPurpose;
import ua.edu.chnu.awards.auth.repository.OneTimeTokenRepository;
import ua.edu.chnu.awards.common.HashUtils;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.user.entity.User;

import lombok.RequiredArgsConstructor;

/**
 * Issues and redeems single-use tokens sent by email. The raw token leaves the service exactly once.
 */
@Service
@RequiredArgsConstructor
public class OneTimeTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OneTimeTokenRepository repository;
    private final Clock clock;

    /**
     * Creates a token for the user.
     *
     * @param user    owner
     * @param purpose what the token allows
     * @param ttl     lifetime
     * @return the raw token to embed in the email link
     */
    @Transactional
    public String issue(User user, TokenPurpose purpose, Duration ttl) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(OneTimeToken.builder()
            .user(user)
            .tokenHash(hash(raw))
            .purpose(purpose)
            .expiresAt(clock.instant().plus(ttl))
            .build());
        return raw;
    }

    /**
     * Looks a raw token up without consuming it.
     *
     * @param raw     the token from the link
     * @param purpose expected purpose
     * @return the token when it is still usable for the purpose, empty otherwise
     */
    @Transactional(readOnly = true)
    public Optional<OneTimeToken> peek(String raw, TokenPurpose purpose) {
        return repository.findByTokenHashAndPurpose(hash(raw), purpose)
            .filter(token -> token.isUsableAt(clock.instant()));
    }

    /**
     * Redeems a raw token: marks it used and returns it when it is valid for the purpose.
     *
     * @param raw     the token from the link
     * @param purpose expected purpose
     * @return the token, empty when unknown, expired or already used
     */
    @Transactional
    public Optional<OneTimeToken> redeem(String raw, TokenPurpose purpose) {
        String hash = hash(raw);
        if (repository.redeem(hash, purpose, clock.instant()) != 1) {
            return Optional.empty();
        }
        return repository.findByTokenHashAndPurpose(hash, purpose);
    }

    /**
     * Owner of a still usable token, without consuming it.
     *
     * @param raw     the token from the link
     * @param purpose expected purpose
     * @return the owner
     * @throws ApiProblemException 410 {@code token-invalid} when the token is unknown, expired or used
     */
    @Transactional(readOnly = true)
    public User peekOwner(String raw, TokenPurpose purpose) {
        return peek(raw, purpose).map(OneTimeToken::getUser).orElseThrow(OneTimeTokenService::gone);
    }

    /**
     * Redeems a token and returns its owner.
     *
     * @param raw     the token from the link
     * @param purpose expected purpose
     * @return the owner
     * @throws ApiProblemException 410 {@code token-invalid} when the token is unknown, expired or used
     */
    @Transactional
    public User redeemOwner(String raw, TokenPurpose purpose) {
        return redeem(raw, purpose).map(OneTimeToken::getUser).orElseThrow(OneTimeTokenService::gone);
    }

    private static ApiProblemException gone() {
        return new ApiProblemException(HttpStatus.GONE, "token-invalid",
            "The link is invalid, expired or already used");
    }

    /**
     * Cancels every unused token of the user for the purpose, so older links in the inbox stop working.
     *
     * @param user    owner
     * @param purpose the purpose
     * @return number of tokens cancelled
     */
    @Transactional
    public int invalidate(User user, TokenPurpose purpose) {
        return repository.cancelUnused(user.getId(), purpose, clock.instant());
    }

    static String hash(String raw) {
        return HashUtils.sha256Hex(raw);
    }
}
