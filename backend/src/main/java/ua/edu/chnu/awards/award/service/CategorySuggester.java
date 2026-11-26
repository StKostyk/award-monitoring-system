package ua.edu.chnu.awards.award.service;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.dto.CategorySuggestion;
import ua.edu.chnu.awards.award.dto.SuggestionReason;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.award.repository.CategorySuggestionQueries;
import ua.edu.chnu.awards.award.repository.CategorySuggestionQueries.Candidate;

import lombok.RequiredArgsConstructor;

/**
 * Rule-based category suggestion for the award being entered. Scores add up per category: each keyword of a
 * subcategory found in the title or the awarding organisation, together with the level keywords of its root;
 * each level keyword of a root; the level of the university unit the awarding organisation names, given to the
 * root and to subcategories with a keyword of their own; and, with the lowest weight, the caller's most used
 * categories. Only the caller's own awards are read.
 */
@Service
@RequiredArgsConstructor
public class CategorySuggester {

    static final int LIMIT = 3;
    static final int CATEGORY_KEYWORD = 30;
    static final int LEVEL_KEYWORD = 40;
    static final int ORGANISATION = 50;
    static final int HISTORY = 15;
    static final int HISTORY_STEP = 5;

    private static final int SHORTEST_INPUT = 3;
    private static final int LONGEST_INPUT = 1000;

    private final CategorySuggestionQueries queries;
    private final OrganizationMatcher organizations;
    private final AccessScope access;

    /**
     * Up to three active categories for an award, the best first; an input shorter than three characters is
     * ignored.
     *
     * @param title        the award title in either language
     * @param organization the awarding organisation
     * @return the suggestions, empty when neither input is usable; with usable input and no keyword or
     *     organisation match only the caller's most used categories
     */
    public List<CategorySuggestion> suggest(String title, String organization) {
        String usableTitle = usable(title);
        String usableOrganization = usable(organization);
        if (usableTitle == null && usableOrganization == null) {
            return List.of();
        }
        SuggestionText text = SuggestionText.of(usableTitle, usableOrganization);
        RecognitionLevel unitLevel = organizations.level(SuggestionText.of(usableOrganization)).orElse(null);
        List<Candidate> candidates = queries.activeCategories();
        Map<Long, Candidate> byId = new HashMap<>();
        Map<Long, Integer> hits = new HashMap<>();
        for (Candidate candidate : candidates) {
            byId.put(candidate.id(), candidate);
            hits.put(candidate.id(), (int) candidate.keywords().stream().filter(text::contains).count());
        }
        Map<Long, Integer> history = history();
        return candidates.stream()
            .map(candidate -> score(candidate, hits.get(candidate.id()), rootHits(candidate, byId, hits), unitLevel,
                history))
            .filter(suggestion -> suggestion.score() > 0)
            .sorted(Comparator.comparingInt(CategorySuggestion::score).reversed())
            .limit(LIMIT)
            .toList();
    }

    private static int rootHits(Candidate candidate, Map<Long, Candidate> byId, Map<Long, Integer> hits) {
        Candidate current = candidate;
        for (int depth = 0; current.parentId() != null && depth < byId.size(); depth++) {
            current = byId.get(current.parentId());
            if (current == null) {
                return 0;
            }
        }
        return current.parentId() == null ? hits.get(current.id()) : 0;
    }

    private static CategorySuggestion score(Candidate candidate, int own, int rootHits, RecognitionLevel unitLevel,
                                            Map<Long, Integer> history) {
        boolean root = candidate.parentId() == null;
        Set<SuggestionReason> reasons = EnumSet.noneOf(SuggestionReason.class);
        int score = 0;
        if (own > 0) {
            int inherited = root ? 0 : rootHits * LEVEL_KEYWORD;
            score += own * (root ? LEVEL_KEYWORD : CATEGORY_KEYWORD) + inherited;
            reasons.add(SuggestionReason.KEYWORD);
        }
        if ((root || own > 0) && candidate.level() == unitLevel) {
            score += ORGANISATION;
            reasons.add(SuggestionReason.ORGANISATION);
        }
        Integer used = history.get(candidate.id());
        if (used != null) {
            score += used;
            reasons.add(SuggestionReason.HISTORY);
        }
        return new CategorySuggestion(candidate.id(), candidate.name(), candidate.nameUk(), candidate.level(), score,
            List.copyOf(reasons));
    }

    private Map<Long, Integer> history() {
        List<Long> used = queries.mostUsedCategories(access.callerId(), LIMIT);
        Map<Long, Integer> points = new HashMap<>();
        for (int rank = 0; rank < used.size(); rank++) {
            points.put(used.get(rank), HISTORY - rank * HISTORY_STEP);
        }
        return points;
    }

    private static String usable(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.strip();
        if (trimmed.length() < SHORTEST_INPUT) {
            return null;
        }
        return trimmed.length() > LONGEST_INPUT ? trimmed.substring(0, LONGEST_INPUT) : trimmed;
    }
}
