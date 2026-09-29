package ua.edu.chnu.awards.award.service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Text entered for an award as lower-case words, one list per input field, so that a keyword never spans two
 * fields. Apostrophes are dropped and every other character that is neither a letter nor a digit separates
 * words.
 */
public final class SuggestionText {

    private static final Pattern APOSTROPHES = Pattern.compile("['’ʼ‘`]");
    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{Nd}]+");
    private static final int SHORTEST_STEM = 4;
    private static final int ENDING = 2;

    private final List<List<String>> parts;

    private SuggestionText(List<List<String>> parts) {
        this.parts = parts;
    }

    /**
     * The words of the given fields; empty or missing fields are left out.
     *
     * @param fields the entered values
     * @return the text
     */
    public static SuggestionText of(String... fields) {
        return new SuggestionText(Arrays.stream(fields).map(SuggestionText::words).filter(words -> !words.isEmpty())
            .toList());
    }

    /**
     * A word without its last two letters, keeping at least four, so that a name matches its inflected forms.
     *
     * @param word a word of a name
     * @return the lower-case stem
     */
    public static String stem(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        return lower.substring(0, Math.min(lower.length(), Math.max(SHORTEST_STEM, lower.length() - ENDING)));
    }

    /**
     * Normalised words of a value.
     *
     * @param value the value, may be null
     * @return lower-case words in order
     */
    public static List<String> words(String value) {
        if (value == null) {
            return List.of();
        }
        String plain = APOSTROPHES.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("");
        return SEPARATORS.splitAsStream(plain).filter(word -> !word.isEmpty()).toList();
    }

    /**
     * All words of the text.
     *
     * @return words of every part in order
     */
    public List<String> words() {
        return parts.stream().flatMap(List::stream).toList();
    }

    /**
     * Whether nothing was entered.
     *
     * @return true without any word
     */
    public boolean isEmpty() {
        return parts.isEmpty();
    }

    /**
     * Whether some word of the text starts with the given stem.
     *
     * @param stem lower-case stem
     * @return true when a word starts with it
     */
    public boolean hasWordStartingWith(String stem) {
        return parts.stream().flatMap(List::stream).anyMatch(word -> word.startsWith(stem));
    }

    /**
     * Whether consecutive words of one part start with the stems of the keyword, in order.
     *
     * @param keyword one or more stems separated by spaces
     * @return true when the keyword is found
     */
    public boolean contains(String keyword) {
        List<String> stems = words(keyword);
        return !stems.isEmpty() && parts.stream().anyMatch(words -> containsIn(words, stems));
    }

    private static boolean containsIn(List<String> words, List<String> stems) {
        for (int start = 0; start + stems.size() <= words.size(); start++) {
            if (startsAt(words, stems, start)) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsAt(List<String> words, List<String> stems, int start) {
        for (int i = 0; i < stems.size(); i++) {
            if (!words.get(start + i).startsWith(stems.get(i))) {
                return false;
            }
        }
        return true;
    }
}
