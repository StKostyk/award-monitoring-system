package ua.edu.chnu.awards.award.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.authz.DelegatedScope;
import ua.edu.chnu.awards.award.dto.ImportField;
import ua.edu.chnu.awards.award.dto.ImportProblem;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.UserAccounts;

import lombok.RequiredArgsConstructor;

/**
 * Finds the member of the row's department with the row's name, ignoring case and apostrophes: an initial
 * matches the first names that start with it, and an address picks among several people of one name. With no
 * match the import adds a person without an account; an initial alone or several matches block the row.
 */
@Component
@RequiredArgsConstructor
public class ImportPeople {

    private final UserAccounts users;
    private final AccessScope access;

    /**
     * Sets the owner of every draft that has a name and a department; a row of the caller, or of somebody who lent
     * the caller a role, is blocked, since an imported award is stored approved without a review.
     *
     * @param drafts the drafts
     */
    void match(List<ImportDraft> drafts) {
        Map<Long, List<User>> members = new HashMap<>();
        Set<Long> excluded = new HashSet<>();
        excluded.add(access.callerId());
        access.delegations().stream().map(DelegatedScope::delegatorId).forEach(excluded::add);
        for (ImportDraft draft : drafts) {
            if (draft.getName() != null && draft.getDepartment() != null) {
                match(draft, members.computeIfAbsent(draft.getDepartment().getId(), users::findMembers));
                if (draft.getOwner() != null && excluded.contains(draft.getOwner().getId())) {
                    draft.problem(ImportProblem.blocking("owner-is-caller", ImportField.FULL_NAME));
                }
            }
        }
    }

    private static void match(ImportDraft draft, List<User> members) {
        ImportValues.ImportName name = draft.getName();
        List<User> candidates = members.stream().filter(member -> sameName(member, name)).toList();
        if (candidates.size() > 1 && !draft.getEmail().isEmpty()) {
            candidates = candidates.stream()
                .filter(member -> member.hasMailbox() && member.getEmailAddress().equalsIgnoreCase(draft.getEmail()))
                .toList();
        }
        if (candidates.size() == 1) {
            draft.setOwner(candidates.get(0));
        } else if (candidates.size() > 1) {
            draft.problem(ImportProblem.blocking("person-ambiguous", ImportField.FULL_NAME));
        } else if (name.initial()) {
            draft.problem(ImportProblem.blocking("person-initials", ImportField.FULL_NAME));
        } else {
            draft.problem(ImportProblem.notice("person-added", ImportField.FULL_NAME));
        }
    }

    private static boolean sameName(User member, ImportValues.ImportName name) {
        if (!PersonRules.comparable(member.getLastName()).equals(PersonRules.comparable(name.lastName()))) {
            return false;
        }
        String first = PersonRules.comparable(member.getFirstName());
        String wanted = PersonRules.comparable(name.firstName());
        return name.initial() ? first.startsWith(wanted) : first.equals(wanted);
    }
}
