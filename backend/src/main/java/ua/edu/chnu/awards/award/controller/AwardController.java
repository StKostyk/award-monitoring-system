package ua.edu.chnu.awards.award.controller;

import java.net.URI;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.award.dto.AwardForm;
import ua.edu.chnu.awards.award.dto.AwardQuery;
import ua.edu.chnu.awards.award.dto.AwardResponse;
import ua.edu.chnu.awards.award.dto.SubmitRequest;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.service.AwardService;
import ua.edu.chnu.awards.award.service.AwardSubmission;
import ua.edu.chnu.awards.common.web.PageResponse;

import lombok.RequiredArgsConstructor;

/**
 * Awards of the caller from draft to submission, and the reading of submitted awards within the caller's
 * scope.
 */
@RestController
@RequestMapping("/api/v1/awards")
@RequiredArgsConstructor
public class AwardController {

    private static final String CAN_CREATE = "@access.require('award:create')";
    private static final String CAN_UPDATE = "@access.require('award:update:own')";
    private static final String CAN_READ_OWN = "@access.require('award:read:own')";

    private final AwardService awardService;
    private final AwardSubmission submission;

    /**
     * The caller's own awards, newest first.
     *
     * @param status   limit to an award status
     * @param category limit to a category
     * @param dateFrom earliest award date
     * @param dateTo   latest award date
     * @param page     0-based page
     * @param size     page size, capped at 100
     * @return the page
     */
    @GetMapping
    @PreAuthorize(CAN_READ_OWN)
    public PageResponse<AwardResponse> list(@RequestParam(required = false) AwardStatus status,
                                            @RequestParam(required = false) Long category,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(awardService.listOwn(new AwardQuery(status, category, dateFrom, dateTo), page,
            size));
    }

    /**
     * Creates a draft owned by the caller.
     *
     * @param form the form; a title in one language is enough
     * @return 201 with the draft and its location
     */
    @PostMapping
    @PreAuthorize(CAN_CREATE)
    public ResponseEntity<AwardResponse> create(@RequestBody AwardForm form) {
        AwardResponse created = awardService.create(form);
        return ResponseEntity.created(URI.create("/api/v1/awards/" + created.id())).body(created);
    }

    /**
     * One award: the caller's own, or a submitted award inside the caller's reading scope.
     *
     * @param id the award
     * @return the award
     */
    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ_OWN)
    public AwardResponse get(@PathVariable long id) {
        return awardService.get(id);
    }

    /**
     * Replaces the fields of the caller's draft.
     *
     * @param id   the draft
     * @param form the full form with the version last read
     * @return the draft with its new version
     */
    @PutMapping("/{id}")
    @PreAuthorize(CAN_UPDATE)
    public AwardResponse update(@PathVariable long id, @RequestBody AwardForm form) {
        return awardService.update(id, form);
    }

    /**
     * Deletes the caller's draft.
     *
     * @param id the draft
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(CAN_READ_OWN)
    public void delete(@PathVariable long id) {
        awardService.delete(id);
    }

    /**
     * Submits the caller's draft to the faculty secretary.
     *
     * @param id      the draft
     * @param request the version last read
     * @return the pending award with its request
     */
    @PostMapping("/{id}/submit")
    @PreAuthorize(CAN_CREATE)
    public AwardResponse submit(@PathVariable long id, @RequestBody(required = false) SubmitRequest request) {
        return submission.submit(id, request);
    }
}
