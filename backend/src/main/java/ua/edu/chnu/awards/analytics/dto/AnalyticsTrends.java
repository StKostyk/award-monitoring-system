package ua.edu.chnu.awards.analytics.dto;

import java.util.List;

/**
 * The categories and awarding organisations that rose or fell the most between the previous period and the
 * period.
 *
 * @param categories the categories
 * @param sources    the awarding organisations
 */
public record AnalyticsTrends(Directions<CategoryTrend> categories, Directions<SourceTrend> sources) {

    /**
     * The entries of both directions.
     *
     * @param rising  largest positive delta first
     * @param falling largest negative delta first
     * @param <T>     the entry type
     */
    public record Directions<T>(List<T> rising, List<T> falling) {

        /**
         * Copies the lists.
         *
         * @param rising  largest positive delta first
         * @param falling largest negative delta first
         */
        public Directions {
            rising = List.copyOf(rising);
            falling = List.copyOf(falling);
        }
    }
}
