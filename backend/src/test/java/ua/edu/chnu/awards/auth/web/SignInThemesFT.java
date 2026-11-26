package ua.edu.chnu.awards.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.edu.chnu.awards.auth.web.LoginPageFT.html;
import static ua.edu.chnu.awards.auth.web.LoginPageFT.quote;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import ua.edu.chnu.awards.support.AbstractFunctionalTest;

class SignInThemesFT {

    private static void noInlineStyleAndTheThemeOnEveryPage(String theme) {
        for (String path : List.of("/login", "/error")) {
            String page = html(path);

            assertThat(page).contains("signin--" + theme, "/login-assets/signin-" + theme + ".css")
                .doesNotContain("<style", "style=\"", "<script>", "signin-catalogue.css");
        }
    }

    @Nested
    @TestPropertySource(properties = "app.sign-in.theme=sky")
    class Sky extends AbstractFunctionalTest {

        @Test
        void ac1_2_the_sky_shows_two_constellations_shooting_stars_smoke_and_no_faculty() {
            String page = html("/login");

            assertThat(page)
                .contains("sky-constellation--steps", "sky-constellation--book", "СУЗІР’Я СХОДИНОК")
                .contains("sky-shoot--1", "sky-shoot--2", "class=\"wisp", "sky-skyline")
                .contains("Додайте свою зірку", "name=\"password\"", "/login?lang=en")
                .doesNotContainIgnoringCase("факультет");
            assertThat(quote(page)).contains("Цитата дня");
        }

        @Test
        void ac1_4_ac1_8_the_setting_applies_to_the_error_page_without_inline_styles() {
            noInlineStyleAndTheThemeOnEveryPage("sky");
        }
    }

    @Nested
    @TestPropertySource(properties = "app.sign-in.theme=rushnyk")
    class Rushnyk extends AbstractFunctionalTest {

        @Test
        void ac1_3_the_rushnyk_shows_the_band_the_stitched_path_and_four_motifs() {
            String page = html("/login");

            assertThat(page)
                .contains("rk-top__band", "rk-path__step--r", "rk-path__step--k", "rk-path__step--g")
                .contains("rk-motif--star", "rk-motif--kalyna", "rk-motif--tree", "rk-motif--rosette")
                .contains("rk-card__strip", "стібок", "name=\"password\"")
                .doesNotContain("rhomb");
            assertThat(quote(page)).contains("Цитата дня");
        }

        @Test
        void ac1_4_ac1_8_the_setting_applies_to_the_error_page_without_inline_styles() {
            noInlineStyleAndTheThemeOnEveryPage("rushnyk");
        }
    }
}
