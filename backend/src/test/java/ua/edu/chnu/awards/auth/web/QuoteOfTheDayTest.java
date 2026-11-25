package ua.edu.chnu.awards.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import ua.edu.chnu.awards.auth.web.QuoteOfTheDay.Quote;

class QuoteOfTheDayTest {

    private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");
    private static final Locale UK = Locale.forLanguageTag("uk");

    @Test
    void ac1_5_every_quote_has_text_author_year_and_source_in_both_languages() {
        QuoteOfTheDay quotes = at(ZonedDateTime.of(2026, 11, 24, 12, 0, 0, 0, KYIV).toInstant());

        assertThat(quotes.size()).isEqualTo(17);
        for (Locale locale : List.of(UK, Locale.ENGLISH)) {
            assertThat(quotes.fromToday(locale)).allSatisfy(quote -> assertThat(List.of(
                quote.text(), quote.author(), quote.year(), quote.source())).allSatisfy(
                    value -> assertThat(value).isNotBlank()));
        }
    }

    @Test
    void ac1_5_the_day_index_is_the_kyiv_epoch_day_modulo_the_list() {
        Instant noon = ZonedDateTime.of(2026, 11, 24, 12, 0, 0, 0, KYIV).toInstant();
        QuoteOfTheDay quotes = at(noon);

        assertThat(quotes.todayIndex()).isEqualTo((int) (noon.atZone(KYIV).toLocalDate().toEpochDay() % 17));
        assertThat(at(noon.plusSeconds(3600)).today(UK)).isEqualTo(quotes.today(UK));
    }

    @Test
    void ac1_5_the_quote_changes_at_kyiv_midnight_not_at_utc_midnight() {
        ZonedDateTime beforeMidnight = ZonedDateTime.of(2026, 11, 24, 23, 59, 0, 0, KYIV);
        ZonedDateTime afterMidnight = beforeMidnight.plusMinutes(2);
        int before = at(beforeMidnight.toInstant()).todayIndex();

        assertThat(at(afterMidnight.toInstant()).todayIndex()).isEqualTo((before + 1) % 17);
        assertThat(at(beforeMidnight.minusHours(20).toInstant()).todayIndex()).isEqualTo(before);
    }

    @Test
    void ac1_5_after_the_last_quote_the_list_starts_again() {
        ZonedDateTime day = ZonedDateTime.of(2026, 11, 24, 12, 0, 0, 0, KYIV);
        QuoteOfTheDay today = at(day.toInstant());
        int toLast = 16 - today.todayIndex();

        assertThat(at(day.plusDays(toLast).toInstant()).todayIndex()).isEqualTo(16);
        assertThat(at(day.plusDays(toLast + 1L).toInstant()).todayIndex()).isZero();
        assertThat(at(day.plusDays(17).toInstant()).today(UK)).isEqualTo(today.today(UK));
    }

    @Test
    void ac1_5_the_page_language_picks_the_text() {
        Instant firstDay = Instant.EPOCH.plusSeconds(43_200);
        QuoteOfTheDay quotes = at(firstDay);
        Quote ukrainian = quotes.today(UK);
        Quote english = quotes.today(Locale.ENGLISH);

        assertThat(quotes.todayIndex()).isZero();
        assertThat(ukrainian.author()).isEqualTo("Ньютон, І.");
        assertThat(ukrainian.text()).startsWith("Якщо я бачив далі");
        assertThat(english.author()).isEqualTo("Newton, I.");
        assertThat(english.text()).startsWith("If I have seen further");
        assertThat(english.number()).isEqualTo(ukrainian.number()).isEqualTo("0104");
    }

    @Test
    void ac1_1_the_catalogue_cards_start_with_todays_quote_and_hold_every_quote_once() {
        QuoteOfTheDay quotes = at(ZonedDateTime.of(2026, 11, 24, 12, 0, 0, 0, KYIV).toInstant());
        List<Quote> cards = quotes.fromToday(UK);

        assertThat(cards).hasSize(17).first().isEqualTo(quotes.today(UK));
        assertThat(cards).extracting(Quote::number).doesNotHaveDuplicates();
    }

    private static QuoteOfTheDay at(Instant instant) {
        return new QuoteOfTheDay(Clock.fixed(instant, KYIV), new ObjectMapper());
    }
}
