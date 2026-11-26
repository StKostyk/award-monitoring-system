package ua.edu.chnu.awards.analytics.dto;

import java.util.List;

/**
 * Counted awards by awarding organisation in the period.
 *
 * @param sources    the largest sources, highest count first
 * @param otherCount awards of every other source
 */
public record SourceSummary(List<Source> sources, long otherCount) {

    /**
     * Copies the sources.
     *
     * @param sources    the largest sources
     * @param otherCount awards of every other source
     */
    public SourceSummary {
        sources = List.copyOf(sources);
    }

    /**
     * One awarding organisation, its spellings grouped.
     *
     * @param name  the most frequent spelling
     * @param count awards of every spelling
     */
    public record Source(String name, long count) {
    }
}
