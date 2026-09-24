package ua.edu.chnu.awards.user.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.user.dto.UserProfileResponse;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;
import ua.edu.chnu.awards.user.mapper.UserProfileMapper;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Reads user profiles.
 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserProfileMapper mapper;
    private final Clock clock;

    /**
     * Profile of the user identified by the token subject.
     *
     * @param userId the user id
     * @return profile with the roles in effect today
     */
    @Transactional(readOnly = true)
    public UserProfileResponse profileOf(long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        List<UserRole> roles = userRoleRepository.findCurrentByUserId(userId, LocalDate.now(clock));
        return mapper.toProfile(user, roles, userRoleRepository.existsByUserId(userId));
    }
}
