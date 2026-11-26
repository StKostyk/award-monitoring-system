package ua.edu.chnu.awards.auth.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The quote of the sign-in page: the same for every visitor on one Kyiv date, the next one on the next date, from
 * the first again after the last. The list is read once at start.
 */
@Component("quoteOfTheDay")
public final class QuoteOfTheDay {

    /** Classpath location of the quote list. */
    public static final String RESOURCE = "sign-in/quotes.json";

    private static final String ENGLISH = "en";
    private static final int CATALOGUE_STEP = 37;
    private static final int CATALOGUE_START = 104;

    private final Clock clock;
    private final List<Entry> entries;

    /**
     * Reads the quote list.
     *
     * @param clock  clock in the Kyiv zone
     * @param mapper JSON reader
     */
    public QuoteOfTheDay(Clock clock, ObjectMapper mapper) {
        this.clock = clock;
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            this.entries = List.copyOf(mapper.readValue(in, new TypeReference<>() { }));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
        if (entries.isEmpty()) {
            throw new IllegalStateException(RESOURCE + " holds no quotes");
        }
    }

    /**
     * Position of today's quote: days since 1970-01-01 of the Kyiv date, modulo the list length.
     *
     * @return index into the list
     */
    public int todayIndex() {
        return Math.floorMod(LocalDate.now(clock).toEpochDay(), entries.size());
    }

    /**
     * Today's quote in the page language.
     *
     * @param locale page locale; English for {@code en}, Ukrainian otherwise
     * @return the quote of the day
     */
    public Quote today(Locale locale) {
        int index = todayIndex();
        return entries.get(index).in(locale, index);
    }

    /**
     * Every quote in the page language, starting with today's and wrapping round, for the catalogue cards.
     *
     * @param locale page locale
     * @return all quotes, today's first
     */
    public List<Quote> fromToday(Locale locale) {
        int start = todayIndex();
        List<Quote> quotes = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            int index = (start + i) % entries.size();
            quotes.add(entries.get(index).in(locale, index));
        }
        return quotes;
    }

    /**
     * Number of quotes in the list.
     *
     * @return list length
     */
    public int size() {
        return entries.size();
    }

    /**
     * One quote in one language.
     *
     * @param text   the quote, without quotation marks
     * @param author author in catalogue form (surname, initial)
     * @param year   year, date or century
     * @param source work, letter or speech the quote comes from
     * @param number catalogue number printed on the card
     */
    public record Quote(String text, String author, String year, String source, String number) {
    }

    /**
     * A text in both page languages.
     *
     * @param uk Ukrainian text
     * @param en English text
     */
    public record Text(String uk, String en) {

        String in(Locale locale) {
            return ENGLISH.equals(locale.getLanguage()) ? en : uk;
        }
    }

    /**
     * One entry of the quote list.
     *
     * @param text   the quote
     * @param author author
     * @param year   year, date or century
     * @param source source of the quote
     */
    public record Entry(Text text, Text author, Text year, Text source) {

        Quote in(Locale locale, int index) {
            String number = String.format(Locale.ROOT, "%04d", index * CATALOGUE_STEP + CATALOGUE_START);
            return new Quote(text.in(locale), author.in(locale), year.in(locale), source.in(locale), number);
        }
    }
}
