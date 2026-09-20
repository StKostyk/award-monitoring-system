package ua.edu.chnu.awards.auth.web;

import java.util.Arrays;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import ua.edu.chnu.awards.config.AuthProperties;
import ua.edu.chnu.awards.user.entity.AccountStatus;

import lombok.RequiredArgsConstructor;

/**
 * Serves the sign-in page of the authorization server.
 */
@Controller
@RequiredArgsConstructor
public class LoginController {

    private final AuthProperties properties;

    /**
     * Renders the login form; a known error code from a failed attempt is translated by the template.
     *
     * @param error error code set by the failure handler, if any
     * @param model view model
     * @return template name
     */
    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error, Model model) {
        model.addAttribute("error", error == null ? null : "login.error." + knownCode(error));
        model.addAttribute("registerUrl", properties.frontendUrl() + "/register");
        return "login";
    }

    private static String knownCode(String code) {
        boolean known = "BAD_CREDENTIALS".equals(code) || "LOCKED".equals(code)
            || Arrays.stream(AccountStatus.values()).anyMatch(status -> status.name().equals(code));
        return known ? code : "BAD_CREDENTIALS";
    }
}
