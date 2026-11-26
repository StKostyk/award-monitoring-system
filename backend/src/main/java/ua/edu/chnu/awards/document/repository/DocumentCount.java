package ua.edu.chnu.awards.document.repository;

/**
 * The number of documents of one award.
 *
 * @param awardId the award
 * @param count   its documents
 */
public record DocumentCount(Long awardId, Long count) {
}
