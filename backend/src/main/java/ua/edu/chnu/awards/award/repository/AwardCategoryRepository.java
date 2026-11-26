package ua.edu.chnu.awards.award.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ua.edu.chnu.awards.award.entity.AwardCategory;

/**
 * Access to {@link AwardCategory} rows.
 */
public interface AwardCategoryRepository extends JpaRepository<AwardCategory, Long> {

    /**
     * Active categories in display order.
     *
     * @return categories ordered by sort order, then English name
     */
    List<AwardCategory> findByActiveTrueOrderBySortOrderAscNameAsc();
}
