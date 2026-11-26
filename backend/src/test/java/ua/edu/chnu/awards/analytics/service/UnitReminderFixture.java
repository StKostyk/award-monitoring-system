package ua.edu.chnu.awards.analytics.service;

import java.util.Optional;

import ua.edu.chnu.awards.organization.OrganizationTree;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;

/**
 * Units and accounts of the reminder tests.
 */
final class UnitReminderFixture {

    private UnitReminderFixture() {
    }

    static Optional<OrganizationTree.Node> node(long id, OrganizationType type, Long parentId) {
        return Optional.of(new OrganizationTree.Node(id, type, parentId, parentId == null ? 1 : 2, true));
    }

    static Organization department(long id) {
        return Organization.builder().id(id).name("Algebra and Informatics")
            .nameUk("Кафедра алгебри та інформатики").code("C64").orgType(OrganizationType.DEPARTMENT).active(true)
            .build();
    }

    static User user(long id, String firstName, long organizationId, AccountStatus status) {
        return User.builder().id(id).firstName(firstName).lastName("Коваль")
            .emailAddress(id == 8L ? "colleague8@chnu.edu.ua" : "user" + id + "@chnu.edu.ua")
            .organization(Organization.builder().id(organizationId).build()).accountStatus(status).build();
    }
}
