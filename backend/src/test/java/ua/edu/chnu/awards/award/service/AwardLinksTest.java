package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AwardLinksTest {

    @Test
    void anAwardLinkPointsToTheAwardPageOfTheBrowserApplication() {
        assertThat(new AwardLinks("http://localhost:4200").awardLink(5L)).isEqualTo("http://localhost:4200/awards/5");
    }
}
