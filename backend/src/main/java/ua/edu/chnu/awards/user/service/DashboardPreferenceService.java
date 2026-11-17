package ua.edu.chnu.awards.user.service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.user.dto.Dashboard;
import ua.edu.chnu.awards.user.dto.DashboardLayout;
import ua.edu.chnu.awards.user.dto.DashboardPreferences;
import ua.edu.chnu.awards.user.repository.DashboardPreferenceQueries;

import lombok.RequiredArgsConstructor;

/**
 * The colour scheme and the dashboard arrangement of a person; a display choice, so nothing is audited.
 */
@Service
@RequiredArgsConstructor
public class DashboardPreferenceService {

    static final String DEFAULT_SCHEME = "SYSTEM";

    private static final Set<String> SCHEMES = Set.of(DEFAULT_SCHEME, "LIGHT", "DARK");
    private static final Set<String> DASHBOARDS = Arrays.stream(Dashboard.values()).map(Enum::name)
        .collect(Collectors.toUnmodifiableSet());
    private static final String LAYOUTS = "layouts";

    private final DashboardPreferenceQueries queries;

    /**
     * The choice of a person; the system scheme and the default layouts until one was saved.
     *
     * @param userId the person
     * @return the scheme and the stored layouts
     */
    @Transactional(readOnly = true)
    public DashboardPreferences preferences(long userId) {
        return queries.find(userId).orElseGet(() -> new DashboardPreferences(DEFAULT_SCHEME, Map.of()));
    }

    /**
     * Replaces the whole choice of a person; a missing scheme is the system one, a missing dashboard its default.
     *
     * @param userId  the person
     * @param request the scheme and the layouts
     * @return the stored choice
     */
    @Transactional
    public DashboardPreferences update(long userId, DashboardPreferences request) {
        String scheme = request.colorScheme() == null ? DEFAULT_SCHEME : request.colorScheme();
        if (!SCHEMES.contains(scheme)) {
            throw invalid("colorScheme");
        }
        if (!DASHBOARDS.containsAll(request.layouts().keySet())) {
            throw invalid(LAYOUTS);
        }
        Map<String, DashboardLayout> layouts = new LinkedHashMap<>();
        for (Dashboard dashboard : Dashboard.values()) {
            DashboardLayout layout = request.layouts().get(dashboard.name());
            if (layout != null) {
                String field = LAYOUTS + "." + dashboard.name();
                check(dashboard, layout.order(), field + ".order");
                check(dashboard, layout.hidden(), field + ".hidden");
                layouts.put(dashboard.name(), layout);
            }
        }
        DashboardPreferences stored = new DashboardPreferences(scheme, layouts);
        queries.save(userId, stored);
        return stored;
    }

    private static void check(Dashboard dashboard, List<String> names, String field) {
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            if (!dashboard.holds(name) || !seen.add(name)) {
                throw invalid(field);
            }
        }
    }

    private static ApiProblemException invalid(String field) {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-parameter",
            "The value of " + field + " is not valid", Map.of("parameter", field));
    }
}
