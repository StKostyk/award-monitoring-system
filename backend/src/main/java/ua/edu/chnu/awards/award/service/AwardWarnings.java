package ua.edu.chnu.awards.award.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.award.dto.AwardWarning;
import ua.edu.chnu.awards.award.dto.DuplicateMatch;
import ua.edu.chnu.awards.award.entity.Award;

import lombok.RequiredArgsConstructor;

/**
 * The hints shown on drafts: a recent award date and possible duplicates among the owner's awards. Only drafts
 * carry them, so they reach nobody but the owner.
 */
@Component
@RequiredArgsConstructor
public class AwardWarnings {

    private final AwardDateRules dates;
    private final DuplicateFinder duplicates;

    /**
     * The warnings of one award.
     *
     * @param award the award
     * @return its warnings, empty unless it is a draft with something to point out
     */
    public List<AwardWarning> of(Award award) {
        return forDrafts(List.of(award)).getOrDefault(award.getId(), List.of());
    }

    /**
     * The warnings of the drafts among the awards, with one duplicate query for all of them.
     *
     * @param awards the awards
     * @return the warnings by award id; awards that are not drafts are absent
     */
    public Map<Long, List<AwardWarning>> forDrafts(Collection<Award> awards) {
        List<Award> drafts = awards.stream().filter(Award::isDraft).toList();
        Map<Long, List<DuplicateMatch>> matches = drafts.isEmpty() ? Map.of()
            : duplicates.matches(drafts.stream().map(Award::getId).collect(Collectors.toSet()));
        Map<Long, List<AwardWarning>> found = new HashMap<>();
        for (Award draft : drafts) {
            List<AwardWarning> warnings = new ArrayList<>();
            if (dates.isRecent(draft.getAwardDate())) {
                warnings.add(new AwardWarning(AwardWarning.RECENT_DATE, AwardDateRules.AWARD_DATE, List.of()));
            }
            List<DuplicateMatch> same = matches.getOrDefault(draft.getId(), List.of());
            if (!same.isEmpty()) {
                warnings.add(new AwardWarning(AwardWarning.POSSIBLE_DUPLICATE, "title", same));
            }
            found.put(draft.getId(), warnings);
        }
        return found;
    }
}
