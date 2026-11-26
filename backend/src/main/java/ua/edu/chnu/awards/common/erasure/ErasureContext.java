package ua.edu.chnu.awards.common.erasure;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * One erasure in progress: the person, the values captured before anything changed, and what the contributors
 * report on the way (counts for the audit row, names and deleted record identifiers for the audit redaction).
 */
public final class ErasureContext {

    /** The first name every erased account carries. */
    public static final String FIRST_NAME = "Erased";

    private final long userId;
    private final String emailAddress;
    private final Instant at;
    private final LocalDate day;
    private final Set<String> names = new LinkedHashSet<>();
    private final Map<String, Set<Long>> erasedRecords = new TreeMap<>();
    private final Map<String, Integer> counts = new LinkedHashMap<>();

    /**
     * Starts an erasure.
     *
     * @param userId       the person
     * @param emailAddress the address the person signs in with, captured before the scrub
     * @param names        every full name the person held
     * @param at           the moment of the erasure
     * @param day          the day of the erasure in the university's time zone
     */
    public ErasureContext(long userId, String emailAddress, Collection<String> names, Instant at, LocalDate day) {
        this.userId = userId;
        this.emailAddress = emailAddress;
        this.names.addAll(names);
        this.at = at;
        this.day = day;
    }

    /**
     * The person.
     *
     * @return the account identifier
     */
    public long userId() {
        return userId;
    }

    /**
     * The address captured before the scrub, which sessions and authorizations carry as their principal.
     *
     * @return the address
     */
    public String emailAddress() {
        return emailAddress;
    }

    /**
     * The moment of the erasure.
     *
     * @return the instant
     */
    public Instant at() {
        return at;
    }

    /**
     * The day of the erasure.
     *
     * @return the day in the university's time zone
     */
    public LocalDate day() {
        return day;
    }

    /**
     * Every full name the person held, current first.
     *
     * @return the names
     */
    public Set<String> names() {
        return Collections.unmodifiableSet(names);
    }

    /**
     * The name shown wherever the person's records remain.
     *
     * @return {@code Erased #<id>}
     */
    public String pseudonym() {
        return FIRST_NAME + " " + lastName();
    }

    /**
     * The last name of the erased account.
     *
     * @return {@code #<id>}
     */
    public String lastName() {
        return "#" + userId;
    }

    /**
     * The address of the erased account, which keeps the unique index and can never receive mail.
     *
     * @return {@code erased-<id>@erased.invalid}
     */
    public String erasedEmailAddress() {
        return "erased-" + userId + "@erased.invalid";
    }

    /**
     * Records records of a table that were deleted or whose names were replaced, for the audit redaction.
     *
     * @param table the table, such as {@code awards}
     * @param ids   the record identifiers
     */
    public void erased(String table, Collection<Long> ids) {
        if (!ids.isEmpty()) {
            erasedRecords.computeIfAbsent(table, key -> new TreeSet<>()).addAll(ids);
        }
    }

    /**
     * The records reported so far by table.
     *
     * @return table to sorted identifiers
     */
    public Map<String, List<Long>> erasedRecords() {
        Map<String, List<Long>> copy = new TreeMap<>();
        erasedRecords.forEach((table, ids) -> copy.put(table, List.copyOf(ids)));
        return copy;
    }

    /**
     * Adds to a count of the audit row.
     *
     * @param key   what was counted, such as {@code awardsDeleted}
     * @param count how many
     */
    public void count(String key, int count) {
        counts.merge(key, count, Integer::sum);
    }

    /**
     * The counts reported so far, in the order they were first reported.
     *
     * @return key to count
     */
    public Map<String, Integer> counts() {
        return Collections.unmodifiableMap(counts);
    }
}
