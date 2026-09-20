package ua.edu.chnu.awards.auth.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Serves the sign-in page of the authorization server.
 */
@Controller
public class LoginController {

    /**
     * Renders the login form; an error code from a failed attempt is translated by the template.
     *
     * @param error error code set by the failure handler, if any
     * @param model view model
     * @return template name
     */
    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error, Model model) {
        model.addAttribute("error", error == null ? null : "login.error." + error);
        return "login";
    }
}
