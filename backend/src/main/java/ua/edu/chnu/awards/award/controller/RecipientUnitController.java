package ua.edu.chnu.awards.award.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.award.dto.UnitRef;
import ua.edu.chnu.awards.award.service.RecipientUnits;

import lombok.RequiredArgsConstructor;

/**
 * The faculties and departments a faculty secretary or dean may enter awards for.
 */
@RestController
@RequiredArgsConstructor
public class RecipientUnitController {

    private final RecipientUnits recipientUnits;

    /**
     * The units the caller may enter awards for.
     *
     * @return faculties first, then departments; empty without a faculty secretary or dean role
     */
    @GetMapping("/api/v1/awards/recipient-units")
    @PreAuthorize("isAuthenticated()")
    public List<UnitRef> list() {
        return recipientUnits.list();
    }
}
