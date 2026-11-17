package ua.edu.chnu.awards.award.service;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.user.service.WaitingAwards;

import lombok.RequiredArgsConstructor;

/**
 * Counts the awards a user owns for the greeting of a claimed account.
 */
@Component
@RequiredArgsConstructor
public class OwnedAwardCount implements WaitingAwards {

    private final AwardRepository awards;

    @Override
    public long count(Long userId) {
        return awards.countByOwnerId(userId);
    }
}
