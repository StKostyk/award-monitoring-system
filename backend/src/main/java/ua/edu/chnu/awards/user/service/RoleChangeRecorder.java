package ua.edu.chnu.awards.user.service;

import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditLog;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;
import ua.edu.chnu.awards.user.event.RoleAssigned;
import ua.edu.chnu.awards.user.event.RoleRevoked;

import lombok.RequiredArgsConstructor;

/**
 * Leaves the trace of a role change: an audit row in the caller's transaction and, once it commits, a message
 * to the person whose authority changed.
 */
@Component
@RequiredArgsConstructor
public class RoleChangeRecorder {

    private final AuditService audit;
    private final ApplicationEventPublisher events;

    /**
     * Records a granted role and tells the holder.
     *
     * @param actor      who granted it
     * @param assignment the new assignment
     */
    public void granted(User actor, UserRole assignment) {
        audit.record(AuditAction.ROLE_ASSIGNED, AuditLog.AUTHORIZATION, assignment.getUser().getId(),
            details(actor, assignment));
        User holder = assignment.getUser();
        Organization organization = assignment.getOrganization();
        events.publishEvent(new RoleAssigned(holder.getEmailAddress(), holder.getFirstName(),
            assignment.getRoleType(), organization.getName(), nameUk(organization), fullName(actor),
            assignment.getValidFrom(), assignment.getValidTo()));
    }

    /**
     * Records a role taken back and tells the former holder.
     *
     * @param actor      who took it back
     * @param assignment the assignment that has just been ended
     */
    public void revoked(User actor, UserRole assignment) {
        audit.record(AuditAction.ROLE_REVOKED, AuditLog.AUTHORIZATION, assignment.getUser().getId(),
            details(actor, assignment));
        User holder = assignment.getUser();
        Organization organization = assignment.getOrganization();
        events.publishEvent(new RoleRevoked(holder.getEmailAddress(), holder.getFirstName(),
            assignment.getRoleType(), organization.getName(), nameUk(organization), fullName(actor),
            assignment.getValidTo()));
    }

    /**
     * Records a role ended because the holder's department was corrected, without a message of its own.
     *
     * @param actor      who corrected it
     * @param assignment the assignment that has just been ended
     */
    public void superseded(User actor, UserRole assignment) {
        audit.record(AuditAction.ROLE_REVOKED, AuditLog.AUTHORIZATION, assignment.getUser().getId(),
            details(actor, assignment));
    }

    private static Map<String, Object> details(User actor, UserRole assignment) {
        return Map.of(
            "actorId", actor.getId(),
            "role", assignment.getRoleType().name(),
            "organizationId", assignment.getOrganization().getId(),
            "validFrom", assignment.getValidFrom().toString(),
            "validTo", String.valueOf(assignment.getValidTo()));
    }

    private static String nameUk(Organization organization) {
        return organization.getNameUk() == null ? organization.getName() : organization.getNameUk();
    }

    private static String fullName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }
}
