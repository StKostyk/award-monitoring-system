package ua.edu.chnu.awards.auth.web;

import java.util.Arrays;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import ua.edu.chnu.awards.config.AuthProperties;
import ua.edu.chnu.awards.user.entity.AccountStatus;

import lombok.RequiredArgsConstructor;

/**
 * Serves the sign-in page of the authorization server and sends stray visits to the browser application.
 */
@Controller
@RequiredArgsConstructor
public class LoginController {

    private static final String EXPIRED = "EXPIRED";

    private final AuthProperties properties;

    /**
     * Renders the login form; a known error code from a failed attempt is translated by the template. A browser
     * that is already signed in is sent to the application instead.
     *
     * @param error          error code set by the failure handler, if any
     * @param authentication the current principal, if any
     * @param model          view model
     * @return template name or redirect
     */
    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error, Authentication authentication, Model model) {
        if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken)) {
            return toApp();
        }
        model.addAttribute("error", error == null ? null : "login.error." + knownCode(error));
        model.addAttribute("registerUrl", properties.frontendUrl() + "/register");
        model.addAttribute("forgotPasswordUrl", properties.frontendUrl() + "/forgot-password");
        return "login";
    }

    /**
     * The authorization server has no home page; the application lives on the frontend origin.
     *
     * @return redirect to the application
     */
    @GetMapping("/")
    public String home() {
        return toApp();
    }

    private String toApp() {
        return "redirect:" + properties.frontendUrl();
    }

    private static String knownCode(String code) {
        boolean known = "BAD_CREDENTIALS".equals(code) || "LOCKED".equals(code) || EXPIRED.equals(code)
            || Arrays.stream(AccountStatus.values()).anyMatch(status -> status.name().equals(code));
        return known ? code : "BAD_CREDENTIALS";
    }
}
