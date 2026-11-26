package ua.edu.chnu.awards.user.service;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.ActorDirectory;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Names audit actors from the user accounts.
 */
@Component
@RequiredArgsConstructor
public class UserActorDirectory implements ActorDirectory {

    private final UserRepository users;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Actor> actorsOf(Collection<Long> userIds) {
        return users.findAllById(userIds).stream()
            .collect(Collectors.toMap(User::getId, user -> new Actor(user.getFullName(), user.getEmailAddress())));
    }
}
