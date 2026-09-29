package ua.edu.chnu.awards.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.FieldViolation;
import ua.edu.chnu.awards.user.dto.UserUpdateRequest;

class ProfileNameRulesTest {

    private final ProfileNameRules rules = new ProfileNameRules();

    @ParameterizedTest
    @ValueSource(strings = {"Петренко-Коваль", "Ковальʼчук", "О'Коннор", "Мар’яна", "Kostyk", "Анна Марія"})
    void ac12_acceptsNamesOfTheRegistrationRules(String name) {
        assertThat(rules.check(new UserUpdateRequest(null, name))).containsExactly(Map.entry("last_name", name));
    }

    @Test
    void ac12_trimsAndKeepsOnlyTheSentFields() {
        assertThat(rules.check(new UserUpdateRequest("  Олена ", null)))
            .containsExactly(Map.entry("first_name", "Олена"));
        assertThat(rules.check(new UserUpdateRequest(null, null))).isEmpty();
    }

    @Test
    void ac12_refusesEveryInvalidFieldAtOnce() {
        assertThatThrownBy(() -> rules.check(new UserUpdateRequest("   ", "Петренко1")))
            .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                assertThat(problem.getStatus().value()).isEqualTo(422);
                assertThat(problem.getType()).isEqualTo("validation-failed");
                assertThat(violations(problem)).extracting(FieldViolation::field, FieldViolation::code)
                    .containsExactly(
                        tuple("firstName", "required"),
                        tuple("lastName", "invalid"));
            });
    }

    @Test
    void ac12_refusesNamesLongerThanOneHundredCharacters() {
        assertThatThrownBy(() -> rules.check(new UserUpdateRequest("А".repeat(101), null)))
            .isInstanceOfSatisfying(ApiProblemException.class, problem ->
                assertThat(violations(problem)).extracting(FieldViolation::code).containsExactly("too-long"));
        assertThat(rules.check(new UserUpdateRequest("А".repeat(100), null))).hasSize(1);
    }

    @Test
    void ac12_refusesPropertiesThatCannotBeChangedHere() {
        UserUpdateRequest request = new UserUpdateRequest("Олена", null);
        request.unknownProperty("email", "x@chnu.edu.ua");
        request.unknownProperty("roles", List.of());

        assertThatThrownBy(() -> rules.check(request))
            .isInstanceOfSatisfying(ApiProblemException.class, problem ->
                assertThat(violations(problem)).extracting(FieldViolation::field, FieldViolation::code)
                    .containsExactly(
                        tuple("email", "not-allowed"),
                        tuple("roles", "not-allowed")));
    }

    @SuppressWarnings("unchecked")
    private static List<FieldViolation> violations(ApiProblemException problem) {
        return (List<FieldViolation>) problem.getProperties().get("errors");
    }
}
