package ua.edu.chnu.awards.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.OptionalLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.ui.ExtendedModelMap;

import ua.edu.chnu.awards.auth.config.AuthProperties;

class LoginControllerTest {

    private final LoginAwardCount awardCount = mock(LoginAwardCount.class);
    private final LoginController controller = new LoginController(defaults(), awardCount);

    @Test
    void ac4_1_the_model_carries_the_count_and_its_plural_message() {
        when(awardCount.current()).thenReturn(OptionalLong.of(886));
        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(controller.login(null, null, model)).isEqualTo("login");
        assertThat(model)
            .containsEntry("awardCount", 886L)
            .containsEntry("awardCountKey", "login.count.many");
    }

    @Test
    void ac4_2_without_a_count_the_page_still_renders() {
        when(awardCount.current()).thenReturn(OptionalLong.empty());
        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(controller.login("BAD_CREDENTIALS", null, model)).isEqualTo("login");
        assertThat(model).doesNotContainKeys("awardCount", "awardCountKey")
            .containsEntry("error", "login.error.BAD_CREDENTIALS");
    }

    @ParameterizedTest
    @CsvSource({"0,many", "1,one", "2,few", "4,few", "5,many", "11,many", "12,many", "14,many", "21,one",
        "22,few", "111,many", "112,many", "886,many", "1001,one", "1023,few"})
    void ac4_1_ukrainian_plural_forms(long count, String form) {
        assertThat(LoginController.pluralForm(count)).isEqualTo(form);
    }

    private static AuthProperties defaults() {
        return Binder.get(new StandardEnvironment()).bindOrCreate("app.auth", AuthProperties.class);
    }
}
