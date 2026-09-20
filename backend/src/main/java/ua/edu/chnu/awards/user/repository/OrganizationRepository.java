package ua.edu.chnu.awards.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;

/**
 * Access to {@link Organization} rows.
 */
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    @EntityGraph(attributePaths = "parent")
    List<Organization> findByOrgTypeAndActiveTrueOrderByName(OrganizationType orgType);
}
