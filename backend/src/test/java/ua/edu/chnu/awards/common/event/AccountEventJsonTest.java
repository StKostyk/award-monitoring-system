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
import ua.edu.chnu.awards.delegation.event.DelegationCreated;
import ua.edu.chnu.awards.delegation.event.DelegationRevoked;
import ua.edu.chnu.awards.document.event.ObjectsReleased;
import ua.edu.chnu.awards.gdpr.event.DataExported;
import ua.edu.chnu.awards.user.entity.RoleType;
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
        assertRoundTrip(new RoleAssigned("olena@chnu.edu.ua", "Олена", RoleType.FACULTY_SECRETARY, "FMI", "ФМІ",
            "Адміністратор", FROM, TO));
    }

    @Test
    void ac1_8_roleRevoked() throws Exception {
        assertRoundTrip(new RoleRevoked("olena@chnu.edu.ua", "Олена", RoleType.FACULTY_SECRETARY, "FMI", "ФМІ",
            "Адміністратор", TO));
    }

    @Test
    void ac1_8_delegationCreated() throws Exception {
        assertRoundTrip(new DelegationCreated("olena@chnu.edu.ua", "Олена", RoleType.FACULTY_SECRETARY, "FMI",
            "ФМІ", "Ірина Бойко", FROM, null, "Відпустка"));
    }

    @Test
    void ac1_8_delegationRevoked() throws Exception {
        assertRoundTrip(new DelegationRevoked(List.of("olena@chnu.edu.ua", "dean.fmi@chnu.edu.ua"),
            RoleType.FACULTY_SECRETARY, "FMI", "ФМІ", "Олена Петренко", "Ірина Бойко"));
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
