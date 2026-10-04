package ua.edu.chnu.awards.auth.web;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * The university brand of the sign-in and error pages, the same ids as the browser application's
 * {@code brand.json}; an unknown id falls back to the neutral brand.
 */
@Slf4j
@Component("loginBrand")
public class LoginBrand {

    /** Brand used when the configured one is unknown. */
    public static final String NEUTRAL = "neutral";

    private static final Set<String> KNOWN = Set.of("chnu", NEUTRAL);

    private final String id;

    /**
     * Resolves the configured brand.
     *
     * @param configured value of {@code app.brand.id}
     */
    public LoginBrand(@Value("${app.brand.id:chnu}") String configured) {
        if (configured != null && KNOWN.contains(configured)) {
            this.id = configured;
        } else {
            log.warn("Unknown brand '{}', using the neutral brand", configured);
            this.id = NEUTRAL;
        }
    }

    /**
     * The brand id that names the stylesheet, the logo folder and the message keys.
     *
     * @return a known brand id
     */
    public String getId() {
        return id;
    }
}
