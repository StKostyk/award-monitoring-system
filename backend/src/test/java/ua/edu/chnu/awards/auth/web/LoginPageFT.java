package ua.edu.chnu.awards.auth.web;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.support.AbstractFunctionalTest;

class LoginPageFT extends AbstractFunctionalTest {

    private static final int OK = 200;
    private static final List<String> ASSETS = List.of(
        "/login-assets/login.css",
        "/login-assets/brand-chnu.css",
        "/login-assets/brand-neutral.css",
        "/login-assets/scheme.js",
        "/login-assets/chnu/logo.svg",
        "/login-assets/neutral/logo.svg",
        "/login-assets/fonts/nunito-cyrillic-400-normal.woff2",
        "/login-assets/fonts/ibm-plex-sans-latin-600-normal.woff2");

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
    void ac4_styles_fonts_logos_and_the_scheme_script_are_served_without_signing_in() {
        ASSETS.forEach(asset -> given().redirects().follow(false).get(asset).then().statusCode(OK));
    }

    @Test
    void ac4_the_page_needs_no_inline_script_or_style_and_no_other_host() {
        String page = html("/login");

        assertThat(page).doesNotContain("<style", "style=\"", "onload=", "<script>");
        assertThat(page).doesNotContainPattern("(src|href)=\"https?://(?!localhost)");
    }

    private static String html(String path) {
        return given().accept("text/html").redirects().follow(false).get(path).then().extract().asString();
    }
}
