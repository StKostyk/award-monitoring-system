package ua.edu.chnu.awards.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailUtilsTest {

    @Test
    void ac41_normalisesTypedAddresses() {
        assertThat(EmailUtils.normalize(null)).isEmpty();
        assertThat(EmailUtils.normalize("  A\u0000b@X.ua\n")).isEqualTo("ab@x.ua");
        assertThat(EmailUtils.normalize("x".repeat(300))).hasSize(254);
    }
}
