package ua.edu.chnu.awards.analytics.dto;

import java.time.Instant;
import java.util.List;

import ua.edu.chnu.awards.award.dto.MemberCount;
import ua.edu.chnu.awards.award.dto.OwnerAwardCount;
import ua.edu.chnu.awards.award.dto.SourceSpelling;
import ua.edu.chnu.awards.award.dto.UnitAwardCount;

/**
 * The cached aggregates of a unit and period: counts and account ids only, never names or consent.
 *
 * @param generatedAt     when the counts were read
 * @param counts          counted awards of both periods per organisation, year, category and recipient kind
 * @param owners          counted personal awards per owner and organisation, per period
 * @param sources         counted awards of the period per spelling of the awarding organisation
 * @param previousSources counted awards of the previous period per spelling of the awarding organisation
 * @param members         active accounts per organisation
 */
@SuppressWarnings("PMD.DataClass")
public record UnitFigures(Instant generatedAt, List<UnitAwardCount> counts, List<OwnerAwardCount> owners,
                          List<SourceSpelling> sources, List<SourceSpelling> previousSources,
                          List<MemberCount> members) {

    /**
     * Copies the lists.
     *
     * @param generatedAt     when the counts were read
     * @param counts          counted awards per organisation, year, category and recipient kind
     * @param owners          counted personal awards per owner and organisation
     * @param sources         counted awards per spelling of the awarding organisation
     * @param previousSources counted awards of the previous period per spelling
     * @param members         active accounts per organisation
     */
    public UnitFigures {
        counts = List.copyOf(counts);
        owners = List.copyOf(owners);
        sources = List.copyOf(sources);
        previousSources = List.copyOf(previousSources);
        members = List.copyOf(members);
    }

    @Override
    public List<UnitAwardCount> counts() {
        return List.copyOf(counts);
    }

    @Override
    public List<OwnerAwardCount> owners() {
        return List.copyOf(owners);
    }

    @Override
    public List<SourceSpelling> sources() {
        return List.copyOf(sources);
    }

    @Override
    public List<SourceSpelling> previousSources() {
        return List.copyOf(previousSources);
    }

    @Override
    public List<MemberCount> members() {
        return List.copyOf(members);
    }
}
