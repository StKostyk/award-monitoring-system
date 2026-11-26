package ua.edu.chnu.awards.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoginBrandTest {

    @Test
    void ac1_keeps_a_known_brand() {
        assertThat(new LoginBrand("chnu").getId()).isEqualTo("chnu");
        assertThat(new LoginBrand("neutral").getId()).isEqualTo("neutral");
    }

    @Test
    void ac1_falls_back_to_the_neutral_brand_for_an_unknown_id() {
        assertThat(new LoginBrand("oxford").getId()).isEqualTo(LoginBrand.NEUTRAL);
        assertThat(new LoginBrand("../chnu").getId()).isEqualTo(LoginBrand.NEUTRAL);
        assertThat(new LoginBrand("").getId()).isEqualTo(LoginBrand.NEUTRAL);
        assertThat(new LoginBrand(null).getId()).isEqualTo(LoginBrand.NEUTRAL);
    }
}
