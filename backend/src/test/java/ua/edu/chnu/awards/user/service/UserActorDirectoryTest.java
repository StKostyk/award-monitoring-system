package ua.edu.chnu.awards.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.audit.ActorDirectory.Actor;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.repository.UserRepository;

class UserActorDirectoryTest {

    private final UserRepository users = mock(UserRepository.class);
    private final UserActorDirectory directory = new UserActorDirectory(users);

    @Test
    void ac2_5_actorsAreNamedFromTheirAccounts() {
        when(users.findAllById(List.of(3L, 4L))).thenReturn(List.of(TestUsers.person(3L, "dean.fmi@chnu.edu.ua")));

        assertThat(directory.actorsOf(List.of(3L, 4L)))
            .containsExactlyEntriesOf(Map.of(3L, new Actor("Марія Мартинюк", "dean.fmi@chnu.edu.ua")));
    }
}
