package ua.edu.chnu.awards.common.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import com.fasterxml.jackson.databind.ObjectMapper;

import ua.edu.chnu.awards.auth.event.EmailRestored;
import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.delegation.event.DelegationCreated;
import ua.edu.chnu.awards.delegation.event.DelegationRevoked;
import ua.edu.chnu.awards.document.event.ObjectsReleased;
import ua.edu.chnu.awards.gdpr.event.DataExported;
import ua.edu.chnu.awards.user.event.RoleAssigned;
import ua.edu.chnu.awards.user.event.RoleRevoked;

@JsonTest
class AccountEventJsonTest {

    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);
    private static final Instant AT = Instant.parse("2026-10-09T10:15:30Z");

    @Autowired
    private ObjectMapper mapper;

    @Test
    void ac1_8_roleAssigned() throws Exception {
        assertRoundTrip(new RoleAssigned(7L, "olena@chnu.edu.ua", "Олена", RoleType.FACULTY_SECRETARY, "FMI",
            "ФМІ", "Адміністратор", FROM, TO, AT));
    }

    @Test
    void ac1_8_roleRevoked() throws Exception {
        assertRoundTrip(new RoleRevoked(7L, "olena@chnu.edu.ua", "Олена", RoleType.FACULTY_SECRETARY, "FMI", "ФМІ",
            "Адміністратор", TO, AT));
    }

    @Test
    void ac1_8_delegationCreated() throws Exception {
        assertRoundTrip(new DelegationCreated(7L, "olena@chnu.edu.ua", "Олена", RoleType.FACULTY_SECRETARY, "FMI",
            "ФМІ", "Ірина Бойко", FROM, null, "Відпустка", AT));
    }

    @Test
    void ac1_8_delegationRevoked() throws Exception {
        assertRoundTrip(new DelegationRevoked(List.of(
                new DelegationRevoked.Recipient(7L, "olena@chnu.edu.ua", "Ірина Бойко"),
                new DelegationRevoked.Recipient(3L, "dean.fmi@chnu.edu.ua", "Олена Петренко")),
            RoleType.FACULTY_SECRETARY, "FMI", "ФМІ", "Олена Петренко", "Ірина Бойко", TO, AT));
    }

    @Test
    void scrum66_ac1_4_aRevocationStoredWithBareAddressesStillReads() throws Exception {
        String stored = "{\"recipients\":[\"olena@chnu.edu.ua\"],\"role\":\"DEAN\",\"organization\":\"FMI\","
            + "\"organizationUk\":\"ФМІ\",\"delegate\":\"Олена Петренко\",\"actor\":\"Ірина Бойко\"}";

        DelegationRevoked event = mapper.readValue(stored, DelegationRevoked.class);

        assertThat(event.recipients()).containsExactly(DelegationRevoked.Recipient.of("olena@chnu.edu.ua"));
        assertThat(event.occurredAt()).isNull();
    }

    @Test
    void ac1_8_dataExported() throws Exception {
        assertRoundTrip(new DataExported("olena@chnu.edu.ua", "Олена", AT, "203.0.113.7", "Firefox 131"));
    }

    @Test
    void ac1_8_objectsReleased() throws Exception {
        assertRoundTrip(new ObjectsReleased(List.of("awards/5/a.pdf", "awards/5/b.pdf")));
    }

    @Test
    void ac1_8_emailRestored() throws Exception {
        assertRoundTrip(new EmailRestored("olena.new@chnu.edu.ua", "olena@chnu.edu.ua", "Олена"));
    }

    private void assertRoundTrip(Object event) throws Exception {
        String json = mapper.writeValueAsString(event);

        assertThat(mapper.readValue(json, event.getClass())).isEqualTo(event);
    }
}
