package ua.edu.chnu.awards.award.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.authz.OrganizationTree;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;

import lombok.RequiredArgsConstructor;

/**
 * Finds the unit of the university that an awarding organisation names. A unit is named when the code of the
 * university, a faculty or the college (at least three letters) is one of the words, or when more than half of
 * the stems of its English or Ukrainian name start words of the text; words that only say what kind of unit it
 * is ("кафедра", "faculty") do not count as stems, but a name that has one needs it in the text as well. Of
 * several named units a unit inside another named one wins over it when it is covered at least as well
 * ("кафедра ... університету"), otherwise the best covered, then the one with more matching stems, then the
 * deeper one.
 */
@Component
@RequiredArgsConstructor
public class OrganizationMatcher {

    private static final int SHORTEST_WORD = 3;
    private static final int SHORTEST_CODE = 3;
    private static final double MAJORITY = 0.5;
    private static final Set<String> GENERIC = Set.of("факультет", "кафедра", "інститут", "навчально",
        "науковий", "імені", "department", "faculty", "institute", "and", "the", "for");
    private static final List<String> TYPE_STEMS = List.of("кафедр", "факультет", "інститут", "department",
        "faculty", "institut");

    private final OrganizationRepository repository;
    private final OrganizationTree tree;

    /**
     * The recognition level of the unit named by an awarding organisation.
     *
     * @param organization the words of the awarding organisation
     * @return the level of the unit's type, empty when no active unit is named
     */
    public Optional<RecognitionLevel> level(SuggestionText organization) {
        if (organization.isEmpty()) {
            return Optional.empty();
        }
        List<Match> matches = repository.findByActiveTrue().stream()
            .map(unit -> match(unit, organization))
            .flatMap(Optional::stream)
            .toList();
        return matches.stream()
            .filter(match -> matches.stream().noneMatch(inner -> inner.unitId() != match.unitId()
                && inner.coverage() >= match.coverage() && tree.covers(match.unitId(), inner.unitId())))
            .max(Comparator.comparingDouble(Match::coverage).thenComparingInt(Match::matched)
                .thenComparingInt(Match::depth))
            .map(Match::level);
    }

    private static Optional<Match> match(Organization unit, SuggestionText text) {
        RecognitionLevel level = RecognitionLevel.valueOf(unit.getOrgType().name());
        String code = unit.getCode() == null ? "" : unit.getCode().toLowerCase(Locale.ROOT);
        if (code.length() >= SHORTEST_CODE && unit.getDepth() <= 1 && text.words().contains(code)) {
            return Optional.of(new Match(unit.getId(), level, 1.0, 1, unit.getDepth()));
        }
        return Stream.of(unit.getName(), unit.getNameUk())
            .filter(name -> typeWordsPresent(name, text))
            .map(OrganizationMatcher::stems)
            .filter(stems -> !stems.isEmpty())
            .map(stems -> {
                int matched = (int) stems.stream().filter(text::hasWordStartingWith).count();
                return new Match(unit.getId(), level, (double) matched / stems.size(), matched, unit.getDepth());
            })
            .filter(candidate -> candidate.coverage() > MAJORITY)
            .max(Comparator.comparingDouble(Match::coverage));
    }

    private static boolean typeWordsPresent(String name, SuggestionText text) {
        List<String> types = TYPE_STEMS.stream()
            .filter(type -> SuggestionText.words(name).stream().anyMatch(word -> word.startsWith(type)))
            .toList();
        return types.isEmpty() || types.stream().anyMatch(text::hasWordStartingWith);
    }

    private static List<String> stems(String name) {
        return SuggestionText.words(name).stream()
            .filter(word -> word.length() >= SHORTEST_WORD && !GENERIC.contains(word))
            .map(SuggestionText::stem)
            .distinct()
            .toList();
    }

    private record Match(long unitId, RecognitionLevel level, double coverage, int matched, int depth) {
    }
}
