package ua.edu.chnu.awards.delegation.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditLog;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.delegation.entity.RoleDelegation;
import ua.edu.chnu.awards.delegation.event.DelegationCreated;
import ua.edu.chnu.awards.delegation.event.DelegationRevoked;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.User;

import lombok.RequiredArgsConstructor;

/**
 * Leaves the trace of a delegation: an audit row in the caller's transaction and, once it commits, a message
 * to the people whose authority changed.
 */
@Component
@RequiredArgsConstructor
public class DelegationRecorder {

    private final AuditService audit;
    private final ApplicationEventPublisher events;

    /**
     * Records a delegation and tells the delegate.
     *
     * @param delegation the new delegation
     */
    public void created(RoleDelegation delegation) {
        audit.record(AuditAction.DELEGATION_CREATED, AuditLog.AUTHORIZATION, delegation.getDelegate().getId(),
            details(delegation, delegation.getDelegator()));
        User delegate = delegation.getDelegate();
        Organization organization = delegation.getOrganization();
        events.publishEvent(new DelegationCreated(delegate.getEmailAddress(), delegate.getFirstName(),
            delegation.getRoleType(), organization.getName(), nameUk(organization),
            fullName(delegation.getDelegator()), delegation.getValidFrom(), delegation.getValidTo(),
            delegation.getReason()));
    }

    /**
     * Records a delegation taken back and tells the delegate, and the delegator when somebody else did it.
     *
     * @param actor      who took it back
     * @param delegation the delegation that has just been taken back
     */
    public void revoked(User actor, RoleDelegation delegation) {
        audit.record(AuditAction.DELEGATION_REVOKED, AuditLog.AUTHORIZATION, delegation.getDelegate().getId(),
            details(delegation, actor));
        Organization organization = delegation.getOrganization();
        events.publishEvent(new DelegationRevoked(recipients(actor, delegation), delegation.getRoleType(),
            organization.getName(), nameUk(organization), fullName(delegation.getDelegate()),
            fullName(actor)));
    }

    private static List<String> recipients(User actor, RoleDelegation delegation) {
        Set<String> addresses = new LinkedHashSet<>();
        addresses.add(delegation.getDelegate().getEmailAddress());
        if (!Objects.equals(actor.getId(), delegation.getDelegator().getId())) {
            addresses.add(delegation.getDelegator().getEmailAddress());
        }
        return List.copyOf(addresses);
    }

    private static Map<String, Object> details(RoleDelegation delegation, User actor) {
        return Map.of(
            "actorId", actor.getId(),
            "delegatorId", delegation.getDelegator().getId(),
            "delegateId", delegation.getDelegate().getId(),
            "role", delegation.getRoleType().name(),
            "organizationId", delegation.getOrganization().getId(),
            "validFrom", delegation.getValidFrom().toString(),
            "validTo", delegation.getValidTo().toString());
    }

    private static String nameUk(Organization organization) {
        return organization.getNameUk() == null ? organization.getName() : organization.getNameUk();
    }

    private static String fullName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }
}
