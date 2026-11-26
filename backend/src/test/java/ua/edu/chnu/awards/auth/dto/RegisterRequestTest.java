package ua.edu.chnu.awards.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RegisterRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void ac13_2_decomposedNamesPassTheNameRulesAndAreKeptComposed() {
        RegisterRequest request = new RegisterRequest("olena@chnu.edu.ua", "Correct-horse-1",
            Normalizer.normalize("Ївга", Normalizer.Form.NFD), Normalizer.normalize("Олійник", Normalizer.Form.NFD),
            1L, "1.1");

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.firstName()).isEqualTo("Ївга");
        assertThat(request.lastName()).isEqualTo("Олійник");
    }

    @Test
    void ac13_2_absentNamesStayAbsentForTheValidator() {
        RegisterRequest request = new RegisterRequest("olena@chnu.edu.ua", "Correct-horse-1", null, null, 1L, "1.1");

        assertThat(request.firstName()).isNull();
        assertThat(validator.validate(request)).extracting(violation -> violation.getPropertyPath().toString())
            .containsExactlyInAnyOrder("firstName", "lastName");
    }
}
