package ua.edu.chnu.awards.award.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ua.edu.chnu.awards.award.dto.ReviewDecisionRequest.Decision;
import ua.edu.chnu.awards.award.entity.ReviewTemplate;

/**
 * Reviewer comment templates.
 */
public interface ReviewTemplateRepository extends JpaRepository<ReviewTemplate, Long> {

    /**
     * The templates offered for a decision, in list order.
     *
     * @param decision the decision
     * @return the active templates of that decision
     */
    List<ReviewTemplate> findByDecisionAndActiveTrueOrderBySortOrderAscIdAsc(Decision decision);
}
