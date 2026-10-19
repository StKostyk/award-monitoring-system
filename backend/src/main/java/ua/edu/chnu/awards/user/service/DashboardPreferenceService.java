package ua.edu.chnu.awards.user.service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.user.dto.DashboardPreferences;
import ua.edu.chnu.awards.user.dto.DashboardWidget;
import ua.edu.chnu.awards.user.repository.DashboardPreferenceQueries;

import lombok.RequiredArgsConstructor;

/**
 * The widgets a person hid on the personal dashboard; a display choice, so nothing is audited.
 */
@Service
@RequiredArgsConstructor
public class DashboardPreferenceService {

    static final String PARAMETER = "hiddenWidgets";

    private static final Set<String> NAMES = Arrays.stream(DashboardWidget.values()).map(Enum::name)
        .collect(Collectors.toUnmodifiableSet());

    private final DashboardPreferenceQueries queries;

    /**
     * The choice of a person; none hidden until one was saved.
     *
     * @param userId the person
     * @return the hidden widgets
     */
    @Transactional(readOnly = true)
    public DashboardPreferences preferences(long userId) {
        return new DashboardPreferences(queries.find(userId).orElse(List.of()));
    }

    /**
     * Replaces the choice of a person; duplicates collapse and the names keep the display order.
     *
     * @param userId  the person
     * @param request the widgets to hide
     * @return the stored choice
     */
    @Transactional
    public DashboardPreferences update(long userId, DashboardPreferences request) {
        List<String> widgets = request.hiddenWidgets().stream().map(DashboardPreferenceService::widget).distinct()
            .sorted(Comparator.naturalOrder()).map(Enum::name).toList();
        queries.save(userId, widgets);
        return new DashboardPreferences(widgets);
    }

    private static DashboardWidget widget(String name) {
        if (name == null || !NAMES.contains(name)) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-parameter",
                "The value of " + PARAMETER + " is not valid", Map.of("parameter", PARAMETER));
        }
        return DashboardWidget.valueOf(name);
    }
}
