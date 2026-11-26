package ua.edu.chnu.awards.award.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.award.dto.AwardResponse;
import ua.edu.chnu.awards.award.dto.WithdrawRequest;
import ua.edu.chnu.awards.award.service.AwardWithdrawal;

import lombok.RequiredArgsConstructor;

/**
 * The owner's withdrawal of a pending award no reviewer has claimed.
 */
@RestController
@RequestMapping("/api/v1/awards")
@RequiredArgsConstructor
public class WithdrawalController {

    private final AwardWithdrawal withdrawal;

    /**
     * Withdraws the caller's pending award that no reviewer has claimed; it becomes a draft again.
     *
     * @param id   the award
     * @param body the version last read
     * @return the draft with its withdrawn request
     */
    @PostMapping("/{id}/withdraw")
    @PreAuthorize(AwardPermissionConstants.CAN_UPDATE)
    public AwardResponse withdraw(@PathVariable long id, @RequestBody(required = false) WithdrawRequest body) {
        return withdrawal.withdraw(id, body);
    }
}
