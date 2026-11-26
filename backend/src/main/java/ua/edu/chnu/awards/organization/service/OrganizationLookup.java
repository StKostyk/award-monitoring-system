package ua.edu.chnu.awards.organization.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;

import lombok.RequiredArgsConstructor;

/**
 * Reads organisations for other modules.
 */
@Service
@RequiredArgsConstructor
public class OrganizationLookup {

    private final OrganizationRepository organizations;

    /**
     * The organisation with the id.
     *
     * @param id the organisation
     * @return the organisation, empty when unknown
     */
    public Optional<Organization> findById(Long id) {
        return organizations.findById(id);
    }

    /**
     * The organisations with the ids.
     *
     * @param ids the organisations
     * @return the organisations found
     */
    public List<Organization> findAllById(Iterable<Long> ids) {
        return organizations.findAllById(ids);
    }

    /**
     * Every active organisation.
     *
     * @return the organisations
     */
    public List<Organization> findByActiveTrue() {
        return organizations.findByActiveTrue();
    }
}
