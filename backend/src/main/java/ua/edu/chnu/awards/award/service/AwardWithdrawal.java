package ua.edu.chnu.awards.award.service;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.award.dto.AwardResponse;
import ua.edu.chnu.awards.award.dto.WithdrawRequest;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.mapper.AwardMapper;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.award.repository.AwardRequestRepository;
import ua.edu.chnu.awards.common.web.ApiProblemException;

import lombok.RequiredArgsConstructor;

/**
 * Takes a pending award no reviewer has claimed back to its owner as a draft. The request row is locked first,
 * as a claim and a decision lock it, so a withdrawal and a claim of one request run one after the other and the
 * second sees the outcome of the first.
 */
@Service
@RequiredArgsConstructor
public class AwardWithdrawal {

    private static final Set<RequestStatus> WITHDRAWABLE = EnumSet.of(RequestStatus.SUBMITTED, RequestStatus.ESCALATED);

    private final AwardRequestRepository requests;
    private final AwardRepository awards;
    private final AwardOwnership ownership;
    private final AwardMapper mapper;
    private final AuditService audit;

    /**
     * Withdraws the caller's pending award.
     *
     * @param id   the award
     * @param body the version of the award last read
     * @return the award, a draft again, with its withdrawn request
     * @throws AwardNotFoundException when it does not exist or is not the caller's
     * @throws ApiProblemException    409 {@code request-claimed} when a reviewer holds the request,
     *                                409 {@code award-not-pending} when it is not under review,
     *                                409 {@code award-stale} when the version differs
     */
    @Transactional
    public AwardResponse withdraw(long id, WithdrawRequest body) {
        AwardRequest request = requests.findByAwardIdForUpdate(id).orElse(null);
        Award award = awards.findForUpdate(id).filter(ownership::isOwn)
            .orElseThrow(() -> new AwardNotFoundException(id));
        requireWithdrawable(award, request);
        ownership.requireVersion(award, body == null ? null : body.version());
        final RequestStatus from = request.getStatus();
        request.setStatus(RequestStatus.WITHDRAWN);
        request.setDeadline(null);
        award.setStatus(AwardStatus.DRAFT);
        requests.saveAndFlush(request);
        awards.saveAndFlush(award);
        audit.record(AuditAction.AWARD_WITHDRAWN, AuditEntityConstants.AWARDS, award.getOwner().getId(),
            award.getId(), Map.of("requestId", request.getId(), "from", from.name(),
                "level", request.getCurrentLevel().name()));
        return mapper.toResponse(award, request);
    }

    private static void requireWithdrawable(Award award, AwardRequest request) {
        if (request == null || award.getStatus() != AwardStatus.PENDING) {
            throw notPending(award, request);
        }
        if (request.getStatus() == RequestStatus.IN_REVIEW || request.getCurrentReviewer() != null) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "request-claimed",
                "A reviewer is already reviewing the award, so it can no longer be withdrawn", Map.of());
        }
        if (!WITHDRAWABLE.contains(request.getStatus())) {
            throw notPending(award, request);
        }
    }

    private static ApiProblemException notPending(Award award, AwardRequest request) {
        return new ApiProblemException(HttpStatus.CONFLICT, "award-not-pending",
            "The award is not waiting for a review", request == null
                ? Map.of("awardStatus", award.getStatus().name())
                : Map.of("awardStatus", award.getStatus().name(), "requestStatus", request.getStatus().name()));
    }
}
