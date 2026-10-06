package ua.edu.chnu.awards.document.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ua.edu.chnu.awards.document.entity.Document;

/**
 * Documents of awards.
 */
public interface DocumentRepository extends JpaRepository<Document, Long> {

    /**
     * The documents of an award, oldest first, with their uploaders.
     *
     * @param awardId the award
     * @return the documents
     */
    @EntityGraph(attributePaths = "uploadedBy")
    List<Document> findByAwardIdOrderByUploadedAtAscIdAsc(long awardId);

    /**
     * How many documents an award has.
     *
     * @param awardId the award
     * @return the count
     */
    long countByAwardId(long awardId);

    /**
     * How many documents each of several awards has.
     *
     * @param awardIds the awards
     * @return one count per award that has documents
     */
    @Query("""
        select new ua.edu.chnu.awards.document.repository.DocumentCount(d.award.id, count(d))
        from Document d where d.award.id in :awardIds group by d.award.id
        """)
    List<DocumentCount> countByAwardIds(@Param("awardIds") Collection<Long> awardIds);

    /**
     * Total size of the documents a user has uploaded, over all her awards.
     *
     * @param userId the uploader
     * @return the size in bytes, 0 when there are none
     */
    @Query("select coalesce(sum(d.size), 0) from Document d where d.uploadedBy.id = :userId")
    long totalSizeUploadedBy(long userId);

    /**
     * Takes a transaction-scoped advisory lock, so that uploads of one user to different awards check the quota
     * one after the other.
     *
     * @param key the lock key of the user
     * @return always 1
     */
    @Query(value = "select count(*) from (select pg_advisory_xact_lock(:key)) l", nativeQuery = true)
    long lockUploadsOf(long key);

    /**
     * A document of the award with the same content.
     *
     * @param awardId  the award
     * @param checksum SHA-256 of the content, hex
     * @return the document, if any
     */
    Optional<Document> findFirstByAwardIdAndChecksum(long awardId, String checksum);

    /**
     * A document with its award, the award's owner and organisation, ready for the read rule.
     *
     * @param id the document
     * @return the document, if it exists
     */
    @EntityGraph(attributePaths = {"award", "award.owner", "award.organization"})
    Optional<Document> findWithAwardById(long id);

    /**
     * Object keys of the documents of an award.
     *
     * @param awardId the award
     * @return the keys
     */
    @Query("select d.storageKey from Document d where d.award.id = :awardId")
    List<String> storageKeysOfAward(@Param("awardId") long awardId);

    /**
     * Which of the given object keys belong to a document.
     *
     * @param keys object keys
     * @return the keys that have a row
     */
    @Query("select d.storageKey from Document d where d.storageKey in :keys")
    List<String> existingKeys(@Param("keys") Collection<String> keys);
}
