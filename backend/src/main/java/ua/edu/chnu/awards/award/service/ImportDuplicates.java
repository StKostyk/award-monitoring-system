package ua.edu.chnu.awards.award.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * The approved personal awards an imported row may repeat: the same owner and date, and the same title (stored
 * already, the import leaves the row out) or a similar one (a probable duplicate, by the trigram similarity of
 * at least 0.6 that the award form uses).
 */
@Component
@RequiredArgsConstructor
public class ImportDuplicates {

    private static final double THRESHOLD = DuplicateFinder.THRESHOLD;
    private static final int GRAM = 3;
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final String QUERY = """
        select user_id, award_date, title, title_uk
          from awards
         where user_id in (:owners) and award_date in (:dates) and recipient_org_id is null
           and status = 'APPROVED'
        """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * The stored awards of the owners on the dates.
     *
     * @param owners the owners
     * @param dates  the dates
     * @return the awards
     */
    public List<Stored> stored(Collection<Long> owners, Collection<LocalDate> dates) {
        if (owners.isEmpty() || dates.isEmpty()) {
            return List.of();
        }
        return jdbc.query(QUERY, Map.of("owners", owners, "dates", dates), (row, index) -> new Stored(
            row.getLong("user_id"), row.getObject("award_date", LocalDate.class), row.getString("title"),
            row.getString("title_uk")));
    }

    /**
     * Whether two titles are the same, ignoring case, spaces and punctuation.
     *
     * @param one   a title
     * @param other another title, may be null
     * @return true when the same
     */
    public static boolean same(String one, String other) {
        return other != null && plainTitle(one).equals(plainTitle(other));
    }

    /**
     * Whether two titles are similar: the share of their common word trigrams is at least 0.6.
     *
     * @param one   a title
     * @param other another title, may be null
     * @return true when similar
     */
    public static boolean similar(String one, String other) {
        if (other == null) {
            return false;
        }
        Set<String> first = trigrams(one);
        Set<String> second = trigrams(other);
        if (first.isEmpty() || second.isEmpty()) {
            return false;
        }
        Set<String> common = new HashSet<>(first);
        common.retainAll(second);
        Set<String> all = new HashSet<>(first);
        all.addAll(second);
        return (double) common.size() / all.size() >= THRESHOLD;
    }

    static String plainTitle(String title) {
        return NON_WORD.matcher(title.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }

    private static Set<String> trigrams(String title) {
        Set<String> grams = new HashSet<>();
        for (String word : plainTitle(title).split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            String padded = "  " + word + " ";
            for (int index = 0; index + GRAM <= padded.length(); index++) {
                grams.add(padded.substring(index, index + GRAM));
            }
        }
        return grams;
    }

    /**
     * A stored award.
     *
     * @param ownerId   the owner
     * @param awardDate the date
     * @param title     the English title, may be null
     * @param titleUk   the Ukrainian title, may be null
     */
    public record Stored(long ownerId, LocalDate awardDate, String title, String titleUk) {

        /**
         * Whether the award has the title.
         *
         * @param other the title
         * @return true when either title is the same
         */
        public boolean sameTitle(String other) {
            return same(other, title) || same(other, titleUk);
        }

        /**
         * Whether the award has a similar title.
         *
         * @param other the title
         * @return true when either title is similar
         */
        public boolean similarTitle(String other) {
            return similar(other, title) || similar(other, titleUk);
        }
    }
}
