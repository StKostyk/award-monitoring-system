package ua.edu.chnu.awards.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HashUtilsTest {

    @Test
    void sha256HexIsLowerCaseHexOfTheUtf8Bytes() {
        assertThat(HashUtils.sha256Hex("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(HashUtils.sha256Hex("Олена")).hasSize(64).isNotEqualTo(HashUtils.sha256Hex("Олег"));
    }
}
