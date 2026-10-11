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

@JsonTest
class AwardEventJsonTest {

    private static final Instant AT = Instant.parse("2026-10-09T10:15:30Z");

    @Autowired
    private ObjectMapper mapper;

    @Test
    void ac1_8_awardDecided() throws Exception {
        assertRoundTrip(new AwardDecided("olena@chnu.edu.ua", "Олена", 5L, "Certificate", "Грамота",
            RequestStatus.APPROVED, ApprovalLevel.FACULTY_SECRETARY, "Ірина Бойко", "Добре"));
    }

    @Test
    void ac1_8_awardCorrected() throws Exception {
        assertRoundTrip(new AwardCorrected("olena@chnu.edu.ua", "Олена", 5L, "Certificate", "Грамота",
            "Ірина Бойко", "Typo", List.of(new AwardCorrected.Change("title", "Certifcate", "Certificate",
                "Грамта", "Грамота"))));
    }

    @Test
    void ac1_8_overdueNoticed() throws Exception {
        assertRoundTrip(new OverdueNoticed("dean.fmi@chnu.edu.ua", "Ірина Бойко", List.of(
            new OverdueNoticed.Item(5L, "Certificate", "Грамота", "Анастасія Коваль",
                ApprovalLevel.FACULTY_SECRETARY, AT, "Петро Іваненко"))));
    }

    @Test
    void ac1_8_reviewMeasured() throws Exception {
        assertRoundTrip(new ReviewMeasured(ApprovalLevel.DEAN, ReviewDecisionType.APPROVED, true,
            Duration.ofHours(30).plusMillis(250)));
    }

    private void assertRoundTrip(Object event) throws Exception {
        String json = mapper.writeValueAsString(event);

        assertThat(mapper.readValue(json, event.getClass())).isEqualTo(event);
    }
}
