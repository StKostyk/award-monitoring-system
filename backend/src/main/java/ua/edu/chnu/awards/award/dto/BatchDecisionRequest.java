package ua.edu.chnu.awards.award.dto;

import java.util.List;

import ua.edu.chnu.awards.award.dto.ReviewDecisionRequest.Decision;

/**
 * One reviewer decision applied to several awards.
 *
 * @param decision what the reviewer decides for every item
 * @param comment  the comment of every item; required to return or reject
 * @param verified true when the reviewer checked the documents of every item on approval
 * @param items    the awards with the request version last read, 1 to 50, distinct
 */
public record BatchDecisionRequest(Decision decision, String comment, Boolean verified, List<BatchItem> items) {

    /**
     * The single decision of one item.
     *
     * @param item the item
     * @return the decision body of that item
     */
    public ReviewDecisionRequest of(BatchItem item) {
        return new ReviewDecisionRequest(decision, item.requestVersion(), comment, verified);
    }
}
