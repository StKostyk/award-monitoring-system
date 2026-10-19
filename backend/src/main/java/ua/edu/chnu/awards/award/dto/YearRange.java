package ua.edu.chnu.awards.award.dto;

/**
 * Calendar years from one to another, both included.
 *
 * @param from first year
 * @param to   last year
 */
public record YearRange(int from, int to) {
}
