package ua.edu.chnu.awards.award.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.dto.AwardImportResult;
import ua.edu.chnu.awards.award.dto.ImportPerson;
import ua.edu.chnu.awards.award.dto.ImportRow;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardSource;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.repository.AwardCategoryRepository;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.common.SecretUtils;
import ua.edu.chnu.awards.organization.service.OrganizationLookup;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.UserAccounts;

import lombok.RequiredArgsConstructor;

/**
 * Stores the rows of an import inside the caller's transaction: people without an account and without an
 * address for the names no member matched (one per department and name), and an approved award per row with
 * the source {@code IMPORT}, its first version and an {@code AWARD_IMPORTED} audit row naming the file and the
 * row. No review request is opened and nobody is notified.
 */
@Component
@RequiredArgsConstructor
public class ImportStore {

    private static final int SECRET_BYTES = 32;

    private final UserAccounts users;
    private final OrganizationLookup organizations;
    private final AwardRepository awards;
    private final AwardCategoryRepository categories;
    private final AwardHistory history;
    private final AccessScope access;
    private final AuditService audit;
    private final PasswordEncoder passwordEncoder;

    /**
     * Stores the rows that are not left out.
     *
     * @param fileName the file name, recorded in the audit rows
     * @param rows     the rows, none of them blocked
     * @return the counts
     */
    public AwardImportResult store(String fileName, List<ImportRow> rows) {
        Map<String, User> added = new HashMap<>();
        int imported = 0;
        for (ImportRow row : rows) {
            if (row.skipped()) {
                continue;
            }
            User owner = owner(row, added);
            Award award = awards.saveAndFlush(award(row, owner));
            history.created(award);
            audit.record(AuditAction.AWARD_IMPORTED, AuditEntityConstants.AWARDS, access.callerId(), award.getId(),
                Map.of("file", fileName, "row", row.row()));
            imported++;
        }
        return new AwardImportResult(imported, added.size(), (int) rows.stream().filter(ImportRow::skipped).count());
    }

    private User owner(ImportRow row, Map<String, User> added) {
        ImportPerson person = row.person();
        if (person.id() != null) {
            return users.getReferenceById(person.id());
        }
        String key = row.department().id() + ":" + PersonRules.comparable(person.lastName()) + ":"
            + PersonRules.comparable(person.firstName());
        return added.computeIfAbsent(key, ignored -> add(person, row.department().id()));
    }

    private User add(ImportPerson person, long departmentId) {
        User user = users.saveAndFlush(User.builder()
            .emailAddress(User.placeholderAddress())
            .firstName(person.firstName())
            .lastName(person.lastName())
            .passwordHash(passwordEncoder.encode(SecretUtils.randomSecret(SECRET_BYTES)))
            .accountStatus(AccountStatus.UNCLAIMED)
            .organization(organizations.findById(departmentId).orElseThrow())
            .addedById(access.callerId())
            .build());
        audit.record(AuditAction.PERSON_ADDED, AuditEntityConstants.USER, access.callerId(), user.getId(),
            Map.of("organizationId", departmentId, "source", "import"));
        return user;
    }

    private Award award(ImportRow row, User owner) {
        boolean ukrainian = ImportPreviewer.isUkrainian(row.title());
        return Award.builder()
            .owner(owner)
            .organization(organizations.findById(row.department().id()).orElseThrow())
            .category(categories.getReferenceById(row.category().id()))
            .title(ukrainian ? null : row.title())
            .titleUk(ukrainian ? row.title() : null)
            .awardingOrganization(row.awardingOrganization())
            .description(row.notes() == null || ImportPreviewer.isUkrainian(row.notes()) ? null : row.notes())
            .descriptionUk(row.notes() != null && ImportPreviewer.isUkrainian(row.notes()) ? row.notes() : null)
            .awardDate(row.awardDate())
            .status(AwardStatus.APPROVED)
            .impactScore(row.category().level().baseScore())
            .source(AwardSource.IMPORT)
            .build();
    }
}
