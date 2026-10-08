package ua.edu.chnu.awards.award.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.dto.AwardForm;
import ua.edu.chnu.awards.award.dto.AwardQuery;
import ua.edu.chnu.awards.award.dto.AwardResponse;
import ua.edu.chnu.awards.award.dto.AwardWarning;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardCategory;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.mapper.AwardMapper;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.award.repository.AwardSpecifications;
import ua.edu.chnu.awards.common.web.PageResponse;
import ua.edu.chnu.awards.document.service.DocumentService;

import lombok.RequiredArgsConstructor;

/**
 * Award drafts of the caller and the reading of awards: the owner sees all of their awards, others see
 * submitted awards inside the scope of a role that may read them, and nobody else learns that an award exists.
 */
@Service
@RequiredArgsConstructor
public class AwardService {

    private final AwardRepository awards;
    private final RequestLookup requests;
    private final AwardSpecifications specifications;
    private final AwardInputRules rules;
    private final AwardOwnership ownership;
    private final AwardWarnings warnings;
    private final AwardMapper mapper;
    private final AccessScope access;
    private final AwardHistory history;
    private final DocumentService documents;

    /**
     * Creates a draft owned by the caller in the caller's department, or in the unit that received it.
     *
     * @param form the form
     * @return the draft
     */
    @Transactional
    public AwardResponse create(AwardForm form) {
        AwardForm clean = rules.normalize(form);
        Optional<AwardCategory> category = rules.check(clean, Optional.empty());
        Award award = ownership.newDraft();
        apply(award, clean, category);
        ownership.assignRecipient(award, clean.recipientOrganizationId());
        Award created = awards.saveAndFlush(award);
        history.created(created);
        return draftResponse(created);
    }

    /**
     * Replaces the fields of the caller's draft.
     *
     * @param id   the draft
     * @param form the full form with the version last read
     * @return the draft with its new version
     */
    @Transactional
    public AwardResponse update(long id, AwardForm form) {
        Award award = ownership.lockedDraft(id);
        AwardForm clean = rules.normalize(form);
        ownership.requireVersion(award, clean.version());
        apply(award, clean, rules.check(clean, Optional.ofNullable(award.getCategory())));
        ownership.assignRecipient(award, clean.recipientOrganizationId());
        Award saved = awards.saveAndFlush(award);
        history.updated(saved);
        return draftResponse(saved);
    }

    /**
     * Deletes the caller's draft that was never submitted (409 {@code award-has-request} otherwise); the objects
     * of its documents are removed after the commit.
     *
     * @param id the draft
     */
    @Transactional
    public void delete(long id) {
        Award draft = ownership.lockedDraft(id);
        requests.requireNeverSubmitted(id);
        documents.releaseObjectsOf(id);
        awards.delete(draft);
    }

    /**
     * One award the caller may see.
     *
     * @param id the award
     * @return the award with its request
     * @throws AwardNotFoundException when it does not exist or is not visible to the caller
     */
    @Transactional(readOnly = true)
    public AwardResponse get(long id) {
        Award award = ownership.readableWithDetails(id);
        return requests.of(id)
            .map(request -> mapper.toResponse(award, request, warnings.of(award), requests.returnComment(request)))
            .orElseGet(() -> draftResponse(award));
    }

    /**
     * A page of the caller's own awards, newest first.
     *
     * @param query filters
     * @param page  0-based page
     * @param size  page size, capped at 100
     * @return the page
     */
    @Transactional(readOnly = true)
    public Page<AwardResponse> listOwn(AwardQuery query, int page, int size) {
        PageRequest pageable = PageResponse.request(page, size,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Award> found = awards.findAll(specifications.ownedBy(access.callerId(), query), pageable);
        Map<Long, AwardRequest> byAward = requests.byAward(found.map(Award::getId).getContent());
        Map<Long, List<AwardWarning>> hints = warnings.forDrafts(found.getContent());
        return found.map(award -> mapper.toResponse(award, byAward.get(award.getId()),
            hints.getOrDefault(award.getId(), List.of())));
    }

    private AwardResponse draftResponse(Award draft) {
        return mapper.toResponse(draft, null, warnings.of(draft));
    }

    private static void apply(Award award, AwardForm form, Optional<AwardCategory> category) {
        award.setTitle(form.title());
        award.setTitleUk(form.titleUk());
        award.setDescription(form.description());
        award.setDescriptionUk(form.descriptionUk());
        award.setCategory(category.orElse(null));
        award.setAwardingOrganization(form.awardingOrganization());
        award.setAwardDate(form.awardDate());
        award.setExternalUrl(form.externalUrl());
    }
}
