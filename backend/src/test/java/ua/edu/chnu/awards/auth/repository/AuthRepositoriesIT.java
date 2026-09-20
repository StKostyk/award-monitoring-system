package ua.edu.chnu.awards.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import ua.edu.chnu.awards.auth.entity.OneTimeToken;
import ua.edu.chnu.awards.auth.entity.TokenPurpose;
import ua.edu.chnu.awards.auth.entity.UserDevice;
import ua.edu.chnu.awards.support.AbstractJpaSliceTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.User;

class AuthRepositoriesIT extends AbstractJpaSliceTest {

    private static final String HASH = "a".repeat(64);

    @Autowired
    private OneTimeTokenRepository tokenRepository;

    @Autowired
    private UserDeviceRepository deviceRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void ac02_storesOneTimeTokenByHashAndPurpose() {
        User user = persistUser("token@chnu.edu.ua");
        Instant expiry = Instant.now().plus(1, ChronoUnit.DAYS);
        OneTimeToken token = OneTimeToken.builder()
            .user(user)
            .tokenHash(HASH)
            .purpose(TokenPurpose.EMAIL_VERIFICATION)
            .expiresAt(expiry)
            .build();
        entityManager.persistAndFlush(token);

        OneTimeToken found = tokenRepository.findByTokenHashAndPurpose(HASH, TokenPurpose.EMAIL_VERIFICATION)
            .orElseThrow();

        assertThat(found.getUser().getId()).isEqualTo(user.getId());
        assertThat(found.getUsedAt()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.isUsableAt(Instant.now())).isTrue();
        assertThat(tokenRepository.findByTokenHashAndPurpose(HASH, TokenPurpose.PASSWORD_RESET)).isEmpty();
    }

    @Test
    void ac02_rejectsDuplicateTokenHash() {
        User user = persistUser("dup-token@chnu.edu.ua");
        Instant expiry = Instant.now().plus(1, ChronoUnit.HOURS);
        entityManager.persistAndFlush(OneTimeToken.builder()
            .user(user).tokenHash(HASH).purpose(TokenPurpose.PASSWORD_RESET).expiresAt(expiry).build());

        assertThatThrownBy(() -> entityManager.persistAndFlush(OneTimeToken.builder()
            .user(user).tokenHash(HASH).purpose(TokenPurpose.SECURITY_REVOKE).expiresAt(expiry).build()))
            .isInstanceOf(jakarta.persistence.PersistenceException.class);
    }

    @Test
    void ac02_storesOneDevicePerFingerprintAndUser() {
        User user = persistUser("device@chnu.edu.ua");
        UserDevice device = UserDevice.builder()
            .user(user)
            .fingerprint(HASH)
            .browser("Chrome 130")
            .operatingSystem("Windows 11")
            .lastIpAddress("192.168.1.10")
            .build();
        entityManager.persistAndFlush(device);

        UserDevice found = deviceRepository.findByUserIdAndFingerprint(user.getId(), HASH).orElseThrow();

        assertThat(found.getFirstSeenAt()).isNotNull();
        assertThat(found.getLastUsedAt()).isNotNull();
        assertThat(deviceRepository.findByUserIdAndFingerprint(user.getId(), "b".repeat(64))).isEmpty();
        assertThatThrownBy(() -> entityManager.persistAndFlush(UserDevice.builder()
            .user(user).fingerprint(HASH).build()))
            .isInstanceOf(jakarta.persistence.PersistenceException.class);
    }

    private User persistUser(String email) {
        Organization department = entityManager.find(Organization.class, TestUsers.DAI_DEPARTMENT_ID);
        return entityManager.persistAndFlush(TestUsers.user(email, department));
    }
}
