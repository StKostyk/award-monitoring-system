package ua.edu.chnu.awards.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SignInThemeTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withBean(SignInTheme.class);

    @Test
    void ac1_4_keeps_each_known_theme() {
        assertThat(new SignInTheme("catalogue").getId()).isEqualTo("catalogue");
        assertThat(new SignInTheme("sky").getId()).isEqualTo("sky");
        assertThat(new SignInTheme("rushnyk").getId()).isEqualTo("rushnyk");
    }

    @Test
    void ac1_4_an_unset_value_means_the_catalogue() {
        assertThat(new SignInTheme(null).getId()).isEqualTo(SignInTheme.CATALOGUE);
        assertThat(new SignInTheme(" ").getId()).isEqualTo(SignInTheme.CATALOGUE);
        runner.run(context -> assertThat(context.getBean(SignInTheme.class).getId()).isEqualTo("catalogue"));
    }

    @Test
    void ac1_4_the_setting_picks_the_theme() {
        runner.withPropertyValues("app.sign-in.theme=sky")
            .run(context -> assertThat(context.getBean(SignInTheme.class).getId()).isEqualTo("sky"));
    }

    @Test
    void ac1_4_an_unknown_value_stops_the_start_naming_the_three_themes() {
        assertThatIllegalStateException().isThrownBy(() -> new SignInTheme("aurora"))
            .withMessageContaining("catalogue, sky, rushnyk").withMessageContaining("aurora");
        runner.withPropertyValues("app.sign-in.theme=Sky").run(context -> assertThat(context)
            .hasFailed().getFailure().rootCause().hasMessageContaining("catalogue, sky, rushnyk"));
    }
}
