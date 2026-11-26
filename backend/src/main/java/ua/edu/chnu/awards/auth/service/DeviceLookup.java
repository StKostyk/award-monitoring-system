package ua.edu.chnu.awards.auth.service;

import java.util.List;

import org.springframework.stereotype.Service;

import ua.edu.chnu.awards.auth.entity.UserDevice;
import ua.edu.chnu.awards.auth.repository.UserDeviceRepository;

import lombok.RequiredArgsConstructor;

/**
 * Reads the devices of an account for other modules.
 */
@Service
@RequiredArgsConstructor
public class DeviceLookup {

    private final UserDeviceRepository devices;

    /**
     * The devices of an account, most recently used first.
     *
     * @param userId the account
     * @return the devices
     */
    public List<UserDevice> findByUserIdOrderByLastUsedAtDesc(Long userId) {
        return devices.findByUserIdOrderByLastUsedAtDesc(userId);
    }
}
