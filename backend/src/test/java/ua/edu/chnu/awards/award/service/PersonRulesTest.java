package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.text.Normalizer;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import ua.edu.chnu.awards.award.dto.PersonForm;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.FieldViolation;
import ua.edu.chnu.awards.organization.OrganizationTree;
import ua.edu.chnu.awards.organization.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;

class PersonRulesTest {

    private static final long DEPARTMENT = 64L;
    private static final long FACULTY = 9L;

    private final RecipientUnits units = mock(RecipientUnits.class);
    private final OrganizationTree tree = mock(OrganizationTree.class);
    private final PersonRules rules = new PersonRules(units, tree, List.of("chnu.edu.ua"));

    @Test
    void ac1_10_namesMatchIgnoringCaseApostrophesSpacesAndComposition() {
        String decomposed = Normalizer.normalize("Дмитрій", Normalizer.Form.NFD);
        PersonForm form = new PersonForm(" " + decomposed + " ", "Д’ЯКОВ", DEPARTMENT, null, null, null);

        List<User> matches = rules.sameName(form, List.of(person("дмитрій", "Д'яков"), person("Дмитрій", "Яков"),
            person("Дмитро", "Дяков")));

        assertThat(matches).extracting(User::getLastName).containsExactly("Д'яков");
    }

    @Test
    void ac1_10_addressesAreMaskedToTheFirstCharacterAndTheDomain() {
        assertThat(PersonRules.mask("petro.koval@chnu.edu.ua")).isEqualTo("p***@chnu.edu.ua");
        assertThat(PersonRules.mask(null)).isNull();
    }

    @Test
    void ac1_3_aCallerWithoutTheRoleIsRefused() {
        when(units.held()).thenReturn(false);

        assertThatThrownBy(rules::requireRole).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ac1_3_onlyADepartmentInsideTheScopeAndAUniversityAddressPass() {
        covered(DEPARTMENT, OrganizationType.DEPARTMENT);
        covered(FACULTY, OrganizationType.FACULTY);

        PersonForm clean = rules.check(new PersonForm(" Олена ", "Коваль ", DEPARTMENT, " Olena@CHNU.edu.ua ",
            null, null), true);

        assertThat(clean.firstName()).isEqualTo("Олена");
        assertThat(clean.email()).isEqualTo("olena@chnu.edu.ua");
        assertThat(codes(new PersonForm("Олена", "Коваль", FACULTY, "olena@gmail.com", null, null), true))
            .containsExactlyInAnyOrder("organizationId:out-of-scope", "email:institutional");
    }

    @Test
    void ac1_3_anAddressWithASecondAtSignOrSpacesIsRefused() {
        covered(DEPARTMENT, OrganizationType.DEPARTMENT);

        assertThat(codes(new PersonForm("Олена", "Коваль", DEPARTMENT, "a@evil.com@chnu.edu.ua", null, null), true))
            .containsExactly("email:institutional");
        assertThat(codes(new PersonForm("Олена", "Коваль", DEPARTMENT, "olena koval@chnu.edu.ua", null, null), true))
            .containsExactly("email:institutional");
    }

    @Test
    void ac1_6_anAddressOnceEnteredCannotBeRemoved() {
        covered(DEPARTMENT, OrganizationType.DEPARTMENT);

        assertThat(codes(new PersonForm("Олена", "Коваль", DEPARTMENT, " ", null, 1L), false))
            .containsExactly("email:required");
    }

    @Test
    void ac1_3_namesFollowTheRegistrationRules() {
        covered(DEPARTMENT, OrganizationType.DEPARTMENT);

        assertThat(codes(new PersonForm("", "x".repeat(101), DEPARTMENT, null, null, null), true))
            .containsExactlyInAnyOrder("firstName:required", "lastName:too-long");
        assertThat(codes(new PersonForm("Олена1", "Коваль", DEPARTMENT, null, null, null), true))
            .containsExactly("firstName:invalid");
    }

    @Test
    void ac13_2_decomposedNamesAreStoredComposed() {
        covered(DEPARTMENT, OrganizationType.DEPARTMENT);

        PersonForm clean = rules.check(new PersonForm(Normalizer.normalize("Ївга", Normalizer.Form.NFD),
            Normalizer.normalize("Олійник ", Normalizer.Form.NFD), DEPARTMENT, null, null, null), true);

        assertThat(clean.firstName()).isEqualTo("Ївга");
        assertThat(clean.lastName()).isEqualTo("Олійник");
    }

    private void covered(long id, OrganizationType type) {
        when(units.covers(id)).thenReturn(true);
        when(tree.node(id)).thenReturn(Optional.of(new OrganizationTree.Node(id, type, null, 0, true)));
    }

    private List<String> codes(PersonForm form, boolean emailOptional) {
        try {
            rules.check(form, emailOptional);
        } catch (ApiProblemException e) {
            @SuppressWarnings("unchecked")
            List<FieldViolation> errors = (List<FieldViolation>) e.getProperties().get("errors");
            return errors.stream().map(error -> error.field() + ":" + error.code()).toList();
        }
        return List.of();
    }

    private static User person(String firstName, String lastName) {
        return User.builder().firstName(firstName).lastName(lastName).accountStatus(AccountStatus.UNCLAIMED)
            .emailAddress(User.placeholderAddress()).build();
    }
}
