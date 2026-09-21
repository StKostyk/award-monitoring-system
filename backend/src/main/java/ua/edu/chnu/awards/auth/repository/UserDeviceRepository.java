package ua.edu.chnu.awards.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ua.edu.chnu.awards.auth.entity.UserDevice;

/**
 * Access to {@link UserDevice} rows.
 */
public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    Optional<UserDevice> findByUserIdAndFingerprint(Long userId, String fingerprint);

    int deleteByUserId(Long userId);
}
