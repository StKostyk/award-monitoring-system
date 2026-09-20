package ua.edu.chnu.awards.user.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.user.dto.OrganizationRef;
import ua.edu.chnu.awards.user.dto.OrganizationSummary;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;

import lombok.RequiredArgsConstructor;

/**
 * Reads the organisation tree.
 */
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository repository;

    /**
     * Active organisations of a level, with their parent.
     *
     * @param type the level
     * @return summaries ordered by name
     */
    @Transactional(readOnly = true)
    public List<OrganizationSummary> activeOfType(OrganizationType type) {
        return repository.findByOrgTypeAndActiveTrueOrderByName(type).stream()
            .map(OrganizationService::toSummary)
            .toList();
    }

    static OrganizationSummary toSummary(Organization organization) {
        Organization parent = organization.getParent();
        OrganizationRef parentRef = parent == null ? null
            : new OrganizationRef(parent.getId(), parent.getName(), parent.getNameUk(), parent.getCode(),
                parent.getOrgType());
        return new OrganizationSummary(organization.getId(), organization.getName(), organization.getNameUk(),
            organization.getCode(), organization.getOrgType(), parentRef);
    }
}
