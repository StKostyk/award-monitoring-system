package ua.edu.chnu.awards.common.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import com.fasterxml.jackson.databind.ObjectMapper;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.entity.ReviewDecisionType;
import ua.edu.chnu.awards.award.event.AwardCorrected;
import ua.edu.chnu.awards.award.event.AwardDecided;
import ua.edu.chnu.awards.award.event.OverdueNoticed;
import ua.edu.chnu.awards.award.event.ReviewMeasured;
import ua.edu.chnu.awards.award.event.ReviewTakenOver;

@JsonTest
class AwardEventJsonTest {

    private static final Instant AT = Instant.parse("2026-10-09T10:15:30Z");

    @Autowired
    private ObjectMapper mapper;

    @Test
    void ac1_8_awardDecided() throws Exception {
        assertRoundTrip(new AwardDecided(7L, "olena@chnu.edu.ua", "Олена", 5L, "Certificate", "Грамота",
            RequestStatus.APPROVED, ApprovalLevel.FACULTY_SECRETARY, "Ірина Бойко", "Добре", AT));
    }

    @Test
    void scrum66_ac1_4_aDecisionStoredWithoutRecipientIdStillReads() throws Exception {
        String stored = "{\"email\":\"olena@chnu.edu.ua\",\"firstName\":\"Олена\",\"awardId\":5,"
            + "\"title\":\"Certificate\",\"titleUk\":\"Грамота\",\"outcome\":\"APPROVED\",\"level\":\"DEAN\","
            + "\"reviewer\":\"Ірина Бойко\",\"comment\":null}";

        AwardDecided event = mapper.readValue(stored, AwardDecided.class);

        assertThat(event.email()).isEqualTo("olena@chnu.edu.ua");
        assertThat(event.userId()).isNull();
        assertThat(event.occurredAt()).isNull();
    }

    @Test
    void ac1_8_awardCorrected() throws Exception {
        assertRoundTrip(new AwardCorrected(7L, "olena@chnu.edu.ua", "Олена", 5L, "Certificate", "Грамота",
            "Ірина Бойко", "Typo", List.of(new AwardCorrected.Change("title", "Certifcate", "Certificate",
                "Грамта", "Грамота")), AT));
    }

    @Test
    void ac1_8_overdueNoticed() throws Exception {
        assertRoundTrip(new OverdueNoticed(3L, "dean.fmi@chnu.edu.ua", "Ірина Бойко", List.of(
            new OverdueNoticed.Item(5L, "Certificate", "Грамота", "Анастасія Коваль",
                ApprovalLevel.FACULTY_SECRETARY, AT, "Петро Іваненко")), AT));
    }

    @Test
    void ac1_8_reviewMeasured() throws Exception {
        assertRoundTrip(new ReviewMeasured(ApprovalLevel.DEAN, ReviewDecisionType.APPROVED, true,
            Duration.ofHours(30).plusMillis(250)));
    }

    @Test
    void ac1_8_reviewTakenOver() throws Exception {
        assertRoundTrip(new ReviewTakenOver(3L, "dean.fmi@chnu.edu.ua", "Ірина", 5L, "Certificate", "Грамота",
            "Петро Іваненко", AT));
    }

    private void assertRoundTrip(Object event) throws Exception {
        String json = mapper.writeValueAsString(event);

        assertThat(mapper.readValue(json, event.getClass())).isEqualTo(event);
    }
}
