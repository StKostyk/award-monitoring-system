package ua.edu.chnu.awards.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.ui.ExtendedModelMap;

import ua.edu.chnu.awards.auth.config.AuthProperties;

class LoginControllerTest {

    private final LoginController controller = new LoginController(defaults());

    @Test
    void ac4_the_model_carries_no_award_count() {
        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(controller.login(null, null, null, model)).isEqualTo("login");
        assertThat(model).doesNotContainKeys("awardCount", "awardCountKey")
            .containsKeys("registerUrl", "forgotPasswordUrl");
    }

    @Test
    void a_failed_attempt_keeps_its_known_error_code() {
        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(controller.login("BAD_CREDENTIALS", null, null, model)).isEqualTo("login");
        assertThat(model).containsEntry("error", "login.error.BAD_CREDENTIALS");
    }

    private static AuthProperties defaults() {
        return Binder.get(new StandardEnvironment()).bindOrCreate("app.auth", AuthProperties.class);
    }
}
