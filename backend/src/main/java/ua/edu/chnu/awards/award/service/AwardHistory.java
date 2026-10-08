package ua.edu.chnu.awards.award.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.dto.AwardVersionResponse;
import ua.edu.chnu.awards.award.dto.FieldChange;
import ua.edu.chnu.awards.award.dto.UserRef;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardSnapshot;
import ua.edu.chnu.awards.award.entity.AwardVersion;
import ua.edu.chnu.awards.award.entity.VersionAction;
import ua.edu.chnu.awards.award.repository.AwardVersionRepository;
import ua.edu.chnu.awards.common.web.PageResponse;
import ua.edu.chnu.awards.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * The saved versions of an award: one is written with every change that moved the award's version, and they
 * are read with the field changes against the version before. The owner sees every version; other readers see
 * the award from its submission on, since a draft is private work.
 */
@Service
@RequiredArgsConstructor
public class AwardHistory {

    /** Largest page of versions. */
    public static final int MAX_SIZE = 50;

    private final AwardVersionRepository versions;
    private final UserRepository users;
    private final AwardOwnership ownership;
    private final AccessScope access;

    /**
     * Records a new draft as its first version, inside the caller's transaction.
     *
     * @param award the draft, flushed so that its id and version number are set
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void created(Award award) {
        record(award, VersionAction.CREATED);
    }

    /**
     * Records a saved draft, inside the caller's transaction, unless the save changed nothing.
     *
     * @param award the draft, flushed so that its version number is current
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void updated(Award award) {
        record(award, VersionAction.UPDATED);
    }

    /**
     * Records the submitted award, inside the caller's transaction.
     *
     * @param award the award, flushed so that its version number is current
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void submitted(Award award) {
        record(award, VersionAction.SUBMITTED);
    }

    /**
     * Records the award after a reviewer decision changed its status, inside the caller's transaction.
     *
     * @param award the award, flushed so that its version number is current
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void decided(Award award) {
        record(award, VersionAction.DECIDED);
    }

    /**
     * Records a reviewer's correction of a pending award with its reason, inside the caller's transaction.
     *
     * @param award  the award, flushed so that its version number is current
     * @param reason why the reviewer corrected it
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void corrected(Award award, String reason) {
        record(award, VersionAction.CORRECTED, reason);
    }

    private void record(Award award, VersionAction action) {
        record(award, action, null);
    }

    private void record(Award award, VersionAction action, String comment) {
        AwardVersion previous = versions.findFirstByAwardIdOrderByNumberDesc(award.getId()).orElse(null);
        if (previous != null && previous.getNumber() == award.getVersion()) {
            return;
        }
        AwardSnapshot snapshot = AwardSnapshot.of(award);
        String[] changed = previous == null ? null : changes(previous.getSnapshot(), snapshot).stream()
            .map(FieldChange::field).toArray(String[]::new);
        versions.save(AwardVersion.builder()
            .awardId(award.getId())
            .number(award.getVersion())
            .action(action)
            .actor(users.getReferenceById(access.callerId()))
            .snapshot(snapshot)
            .changedFields(changed)
            .comment(comment)
            .build());
    }

    /**
     * A page of the versions of an award the caller may read, newest first.
     *
     * @param id   the award
     * @param page 0-based page
     * @param size page size, capped at {@link #MAX_SIZE}
     * @return the page
     * @throws AwardNotFoundException when it does not exist or is not readable by the caller
     */
    @Transactional(readOnly = true)
    public Page<AwardVersionResponse> versions(long id, int page, int size) {
        Award award = ownership.readable(id);
        long first = firstVisible(award);
        PageRequest pageable = PageResponse.request(page, Math.min(size, MAX_SIZE),
            Sort.by(Sort.Order.desc("number")));
        Page<AwardVersion> found = versions.findByAwardIdAndNumberGreaterThanEqual(id, first, pageable);
        List<AwardVersion> rows = new ArrayList<>(found.getContent());
        if (!rows.isEmpty() && rows.getLast().getNumber() > first) {
            versions.findFirstByAwardIdAndNumberLessThanOrderByNumberDesc(id, rows.getLast().getNumber())
                .ifPresent(rows::add);
        }
        List<AwardVersionResponse> content = new ArrayList<>();
        for (int i = 0; i < found.getNumberOfElements(); i++) {
            List<FieldChange> changes = i + 1 < rows.size()
                ? changes(rows.get(i + 1).getSnapshot(), rows.get(i).getSnapshot())
                : List.of();
            content.add(response(rows.get(i), changes));
        }
        return new PageImpl<>(content, pageable, found.getTotalElements());
    }

    /**
     * The fields that differ between two snapshots, in field order.
     *
     * @param before the earlier snapshot
     * @param after  the later snapshot
     * @return the changes, empty when equal
     */
    static List<FieldChange> changes(AwardSnapshot before, AwardSnapshot after) {
        Map<String, Object> old = before.fields();
        return after.fields().entrySet().stream()
            .filter(field -> !Objects.equals(old.get(field.getKey()), field.getValue()))
            .map(field -> new FieldChange(field.getKey(), old.get(field.getKey()), field.getValue()))
            .toList();
    }

    private long firstVisible(Award award) {
        if (ownership.isOwn(award)) {
            return Long.MIN_VALUE;
        }
        return versions.firstNumber(award.getId(), VersionAction.SUBMITTED)
            .or(() -> versions.firstNumber(award.getId(), VersionAction.BASELINE))
            .orElse(Long.MIN_VALUE);
    }

    private static AwardVersionResponse response(AwardVersion version, List<FieldChange> changes) {
        return new AwardVersionResponse(version.getNumber(), version.getAction(), UserRef.of(version.getActor()),
            version.getCreatedAt(), version.getSnapshot(), changes, version.getComment());
    }
}
