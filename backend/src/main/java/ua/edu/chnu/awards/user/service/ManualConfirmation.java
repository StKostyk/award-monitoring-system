package ua.edu.chnu.awards.user.service;

import java.util.Optional;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;

/**
 * The only implementation while the university has no staff directory to ask: membership is always confirmed by
 * somebody who may manage the department, so a self-registered account starts with no role at all.
 */
@Component
public class ManualConfirmation implements MembershipConfirmation {

    @Override
    public Optional<UserRole> confirm(User user, Organization department) {
        return Optional.empty();
    }
}
