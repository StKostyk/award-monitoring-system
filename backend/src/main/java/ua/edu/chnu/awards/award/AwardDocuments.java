package ua.edu.chnu.awards.award;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import ua.edu.chnu.awards.award.dto.SharedDocument;
import ua.edu.chnu.awards.award.entity.Award;

/**
 * What awards need from the documents attached to them; implemented by the module that stores the documents.
 */
public interface AwardDocuments {

    /**
     * The number of documents of an award.
     *
     * @param awardId the award
     * @return the count
     */
    long countOf(long awardId);

    /**
     * The number of documents per award; awards without documents are left out.
     *
     * @param awardIds the awards
     * @return the counts by award id
     */
    Map<Long, Long> countsOf(Collection<Long> awardIds);

    /**
     * The documents the owner shows to colleagues, in upload order.
     *
     * @param awardId the award
     * @return the listings
     */
    List<SharedDocument> shownToColleagues(long awardId);

    /**
     * Schedules the removal of the stored objects of an award that is being deleted, for after the commit.
     *
     * @param awardId the award
     */
    void releaseObjectsOf(long awardId);

    /**
     * Hides every shown document of an award that is no longer shared, with an audit row each.
     *
     * @param award the award, locked by the caller
     */
    void hideFromColleagues(Award award);
}
