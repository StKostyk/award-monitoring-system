package ua.edu.chnu.awards.auth.web;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ua.edu.chnu.awards.support.AbstractFunctionalTest;

class LoginPageFT extends AbstractFunctionalTest {

    private static final int OK = 200;
    private static final Locale UK = Locale.forLanguageTag("uk");
    private static final String QUOTE_START = "data-testid=\"quote-of-the-day\"";
    private static final List<String> ASSETS = List.of(
        "/login-assets/login.css",
        "/login-assets/signin-catalogue.css",
        "/login-assets/signin-sky.css",
        "/login-assets/signin-rushnyk.css",
        "/login-assets/brand-chnu.css",
        "/login-assets/brand-neutral.css",
        "/login-assets/scheme.js",
        "/login-assets/chnu/logo.svg",
        "/login-assets/neutral/logo.svg",
        "/login-assets/fonts/manrope-cyrillic-wght-normal.woff2",
        "/login-assets/fonts/manrope-latin-wght-normal.woff2",
        "/login-assets/fonts/onest-cyrillic-wght-normal.woff2",
        "/login-assets/fonts/onest-latin-wght-normal.woff2");

    @Autowired
    private QuoteOfTheDay quotes;

    @Test
    void ac1_the_sign_in_page_shows_the_configured_brand_in_ukrainian() {
        String page = html("/login");

        assertThat(page)
            .contains("/login-assets/brand-chnu.css", "/login-assets/chnu/logo.svg")
            .contains("Облік нагород ЧНУ", "Чернівецький національний університет імені Юрія Федьковича")
            .doesNotContain("brand-neutral");
    }

    @Test
    void ac1_the_brand_follows_the_page_language() {
        assertThat(html("/login?lang=en")).contains("ChNU Awards", "Yuriy Fedkovych Chernivtsi National University");
    }

    @Test
    void ac1_the_error_page_carries_the_brand() {
        assertThat(html("/error")).contains("/login-assets/brand-chnu.css", "Облік нагород ЧНУ");
    }

    @Test
    void ac1_1_the_default_theme_is_the_catalogue_with_a_stamped_card_and_a_footer() {
        String page = html("/login");

        assertThat(page)
            .contains("signin--catalogue", "/login-assets/signin-catalogue.css", "catalogue-card--today")
            .contains("ЦИТАТА ДНЯ", "Каталог досягнень університету", "name=\"password\"")
            .contains("href=\"http://localhost:4200/privacy-notice\"", "Конфіденційність")
            .contains("href=\"http://localhost:4200/terms\"", "Умови користування")
            .doesNotContain("signin-sky.css", "signin-rushnyk.css", "login-award-count");
        assertThat(page.split("catalogue-card--today", -1)).hasSize(3);
    }

    @Test
    void ac1_5_every_visitor_sees_the_same_quote_of_the_day_in_the_page_language() {
        String first = quote(html("/login"));
        String second = quote(html("/login"));
        String english = quote(html("/login?lang=en"));

        assertThat(first).isEqualTo(second).contains(quotes.today(UK).author());
        assertThat(english).contains(quotes.today(Locale.ENGLISH).author(), "Quote of the day");
        assertThat(quotes.today(Locale.ENGLISH).number()).isEqualTo(quotes.today(UK).number());
    }

    @Test
    void ac1_8_the_form_links_and_the_language_switch_stay() {
        assertThat(html("/login"))
            .contains("id=\"forgot-password\"", "http://localhost:4200/forgot-password")
            .contains("http://localhost:4200/register", "Зареєструватися")
            .contains("/login?lang=en", "name=\"_csrf\"");
        assertThat(html("/login?lang=en")).contains("/login?lang=uk", "Forgot password?", "Register");
        assertThat(html("/login?logout")).contains("role=\"status\"", "Ви вийшли з системи.");
        assertThat(html("/login?error=LOCKED")).contains("role=\"alert\"", "Забагато невдалих спроб");
    }

    @Test
    void ac4_styles_fonts_logos_and_the_scheme_script_are_served_without_signing_in() {
        ASSETS.forEach(asset -> given().redirects().follow(false).get(asset).then().statusCode(OK));
    }

    @Test
    void ac4_the_page_needs_no_inline_script_or_style_and_no_other_host() {
        for (String path : List.of("/login", "/error")) {
            String page = html(path);

            assertThat(page).doesNotContain("<style", "style=\"", "onload=", "<script>");
            assertThat(page).doesNotContainPattern("(src|href)=\"https?://(?!localhost)");
        }
    }

    static String quote(String page) {
        int start = page.indexOf(QUOTE_START);
        assertThat(start).as("quote of the day on the page").isNotNegative();
        return page.substring(start, page.indexOf("</figure>", start));
    }

    static String html(String path) {
        return given().accept("text/html").redirects().follow(false).get(path).then().extract().asString();
    }
}
