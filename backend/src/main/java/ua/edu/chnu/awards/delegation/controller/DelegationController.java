package ua.edu.chnu.awards.delegation.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.delegation.dto.DelegationListResponse;
import ua.edu.chnu.awards.delegation.dto.DelegationRequest;
import ua.edu.chnu.awards.delegation.dto.DelegationResponse;
import ua.edu.chnu.awards.delegation.entity.DelegationState;
import ua.edu.chnu.awards.delegation.service.DelegationService;

import lombok.RequiredArgsConstructor;

/**
 * Delegation endpoints: approval authority the caller lends and borrows.
 */
@RestController
@RequestMapping("/api/v1/delegations")
@RequiredArgsConstructor
public class DelegationController {

    private static final String CAN_APPROVE = "@access.require('award:approve:level1')";
    private static final String OWN_PAGE = "#delegatorId == null or @access.require('user:manage')";

    private final DelegationService delegationService;

    /**
     * Delegations given and received, newest first.
     *
     * @param state       show only delegations in this state
     * @param delegatorId whose page to read; the caller's own when omitted, administrators only
     * @return the two sides of the page
     */
    @GetMapping
    @PreAuthorize(OWN_PAGE)
    public DelegationListResponse list(@RequestParam(required = false) DelegationState state,
                                       @RequestParam(required = false) Long delegatorId) {
        return delegationService.list(state, delegatorId);
    }

    /**
     * Lends an approval role the caller holds to a colleague for a bounded period.
     *
     * @param request delegate, role, organisation, period and reason
     * @return the new delegation
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAN_APPROVE)
    public DelegationResponse create(@Valid @RequestBody DelegationRequest request) {
        return delegationService.create(request);
    }

    /**
     * Takes borrowed authority back; the delegate is signed out everywhere.
     *
     * @param id the delegation to end
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void revoke(@PathVariable long id) {
        delegationService.revoke(id);
    }
}
