package ua.edu.chnu.awards.award.dto;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.common.web.ApiProblemException;

/**
 * The search text of an award list, split into the words the full-text and trigram matches use.
 *
 * @param words the lower-cased words, apostrophes removed, at most ten
 * @param text  the trimmed search text in composed (NFC) form, matched as a fragment of a name
 */
public record SearchTerms(List<String> words, String text) {

    /** Shortest search text that is searched. */
    public static final int MIN_LENGTH = 2;

    /** Longest search text accepted. */
    public static final int MAX_LENGTH = 200;

    /** Most words used from one search text. */
    public static final int MAX_WORDS = 10;

    /** Lowest word similarity of a typo match. */
    public static final double MIN_SIMILARITY = 0.5;

    private static final Pattern APOSTROPHES = Pattern.compile("['’ʼ`]");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{M}\\p{N}]+");
    private static final String ENDINGS = "аеєиіїоуюяйь";
    private static final int MIN_STEMMED_LENGTH = 5;
    private static final int MIN_STEM = 4;
    private static final int MAX_ENDING = 2;

    /**
     * Copies the words.
     *
     * @param words the lower-cased words, apostrophes removed, at most ten
     * @param text  the trimmed search text in composed (NFC) form, matched as a fragment of a name
     */
    public SearchTerms {
        words = List.copyOf(words);
    }

    /**
     * Reads a search text.
     *
     * @param query the text as sent, may be null
     * @return the terms, or null when the text is missing or shorter than two characters
     * @throws ApiProblemException when the text is longer than 200 characters
     */
    public static SearchTerms of(String query) {
        String text = query == null ? "" : Normalizer.normalize(query, Normalizer.Form.NFC).strip();
        if (text.length() > MAX_LENGTH) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-parameter",
                "The search text must not be longer than " + MAX_LENGTH + " characters", Map.of("parameter", "q"));
        }
        if (text.length() < MIN_LENGTH) {
            return null;
        }
        String joined = APOSTROPHES.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("");
        List<String> words = Arrays.stream(SEPARATORS.split(joined)).filter(word -> !word.isEmpty())
            .limit(MAX_WORDS).toList();
        return new SearchTerms(words, text);
    }

    /**
     * Tells whether the text holds no letter or digit, so that nothing can match.
     *
     * @return true without words
     */
    public boolean isEmpty() {
        return words.isEmpty();
    }

    /**
     * The full-text query: every stem as a prefix, all required.
     *
     * @return text for {@code to_tsquery('simple', …)}
     */
    public String tsquery() {
        return words.stream().map(word -> stem(word) + ":*").collect(Collectors.joining(" & "));
    }

    /**
     * The words joined for the trigram match of the titles and the awarding organisation.
     *
     * @return the normalised text
     */
    public String trigram() {
        return String.join(" ", words);
    }

    /**
     * The search text as a case-insensitive {@code LIKE} fragment: wildcards escaped, any apostrophe matching any
     * one character and every run of white space read as one space.
     *
     * @return the pattern
     */
    public String namePattern() {
        String escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + SPACES.matcher(APOSTROPHES.matcher(escaped).replaceAll("_")).replaceAll(" ") + "%";
    }

    /**
     * Cuts up to two final vowels or soft signs of a word of five or more characters, keeping four.
     *
     * @param word a lower-cased word
     * @return the stem
     */
    static String stem(String word) {
        if (word.length() < MIN_STEMMED_LENGTH) {
            return word;
        }
        int end = word.length();
        int floor = Math.max(MIN_STEM, end - MAX_ENDING);
        while (end > floor && ENDINGS.indexOf(word.charAt(end - 1)) >= 0) {
            end--;
        }
        return word.substring(0, end);
    }
}
