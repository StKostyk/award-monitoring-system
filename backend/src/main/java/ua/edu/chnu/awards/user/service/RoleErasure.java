package ua.edu.chnu.awards.user.service;

import java.util.List;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.erasure.ErasureContext;
import ua.edu.chnu.awards.common.erasure.ErasureContributor;
import ua.edu.chnu.awards.common.erasure.ErasureOrderConstants;
import ua.edu.chnu.awards.user.entity.UserRole;
import ua.edu.chnu.awards.user.repository.DashboardPreferenceQueries;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Erasure of the person's authority and display choices: every current or future role assignment ends, audited
 * as {@code ROLE_REVOKED} with the reason {@code erasure} and without a message, and the dashboard widget choice
 * is deleted.
 */
@Component
@RequiredArgsConstructor
public class RoleErasure implements ErasureContributor {

    private final UserRoleRepository roles;
    private final RoleChangeRecorder recorder;
    private final DashboardPreferenceQueries dashboardPreferences;

    @Override
    public int order() {
        return ErasureOrderConstants.ROLES;
    }

    @Override
    public void erase(ErasureContext context) {
        List<UserRole> active = roles.findHistoryByUserId(context.userId()).stream()
            .filter(role -> role.getValidTo() == null || !role.getValidTo().isBefore(context.day()))
            .toList();
        active.forEach(role -> {
            role.setValidTo(RoleAssignmentService.lastDayOf(role, context.day()));
            recorder.erased(role);
        });
        roles.saveAllAndFlush(active);
        context.count("rolesEnded", active.size());
        context.count("dashboardPreferencesDeleted", dashboardPreferences.delete(context.userId()));
    }
}
