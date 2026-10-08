package ua.edu.chnu.awards.award.dto;

import ua.edu.chnu.awards.award.dto.ReviewDecisionRequest.Decision;

/**
 * A reviewer comment template in the caller's language.
 *
 * @param id       the template
 * @param decision the decision it is offered for
 * @param title    the name shown in the list
 * @param body     the comment it fills in
 */
public record ReviewTemplateView(long id, Decision decision, String title, String body) {
}
