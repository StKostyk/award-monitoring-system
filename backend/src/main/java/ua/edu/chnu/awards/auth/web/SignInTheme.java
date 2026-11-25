package ua.edu.chnu.awards.auth.web;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The look of the sign-in, error and 429 pages, chosen by the deployment with {@code app.sign-in.theme}; an unknown
 * value stops the start.
 */
@Component("signInTheme")
public final class SignInTheme {

    /** Theme used when the setting is empty. */
    public static final String CATALOGUE = "catalogue";

    /** Every theme the pages can render, in the order the error message names them. */
    public static final List<String> KNOWN = List.of(CATALOGUE, "sky", "rushnyk");

    private final String id;

    /**
     * Validates the configured theme.
     *
     * @param configured value of {@code app.sign-in.theme}
     * @throws IllegalStateException when the value names no known theme
     */
    public SignInTheme(@Value("${app.sign-in.theme:catalogue}") String configured) {
        String value = configured == null || configured.isBlank() ? CATALOGUE : configured.trim();
        if (!KNOWN.contains(value)) {
            throw new IllegalStateException("app.sign-in.theme (APP_SIGN_IN_THEME) must be one of "
                + String.join(", ", KNOWN) + ", not '" + configured + "'");
        }
        this.id = value;
    }

    /**
     * The theme id that names the stylesheet and the decoration fragment.
     *
     * @return a known theme id
     */
    public String getId() {
        return id;
    }
}
