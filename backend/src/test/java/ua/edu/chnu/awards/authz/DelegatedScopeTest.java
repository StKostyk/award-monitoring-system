package ua.edu.chnu.awards.authz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ua.edu.chnu.awards.user.entity.RoleType;

class DelegatedScopeTest {

    @Test
    void ac32_claimCarriesRoleOrganisationAndDelegator() {
        DelegatedScope scope = new DelegatedScope(RoleType.DEAN, 9L, 3L);

        assertThat(scope.toClaim()).isEqualTo("DEAN:9:3");
        assertThat(scope.scope()).isEqualTo(new RoleScope(RoleType.DEAN, 9L));
        assertThat(DelegatedScope.parse("DEAN:9:3")).contains(scope);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEAN:9", "DEAN:9:3:4", "NOPE:9:3", "DEAN:x:3", "DEAN:9:y", ":9:3", ""})
    void malformedEntriesAreIgnored(String claim) {
        assertThat(DelegatedScope.parse(claim)).isEmpty();
    }

    @Test
    void nullIsIgnored() {
        assertThat(DelegatedScope.parse(null)).isEqualTo(Optional.empty());
    }
}
