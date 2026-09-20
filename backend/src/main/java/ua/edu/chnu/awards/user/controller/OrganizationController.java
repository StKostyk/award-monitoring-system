package ua.edu.chnu.awards.user.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.user.dto.OrganizationSummary;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.service.OrganizationService;

import lombok.RequiredArgsConstructor;

/**
 * Organisation directory, readable without a token so registration can offer a department picker.
 */
@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    /**
     * Active organisations of one level with their parent.
     *
     * @param type the level, defaults to departments
     * @return organisations ordered by English name
     */
    @GetMapping
    public List<OrganizationSummary> list(@RequestParam(defaultValue = "DEPARTMENT") OrganizationType type) {
        return organizationService.activeOfType(type);
    }
}
