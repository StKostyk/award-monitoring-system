package ua.edu.chnu.awards.analytics.export;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.common.web.ApiProblemException;

/**
 * The languages the analytics export is labelled in.
 */
public enum ReportLanguage {

    /** Ukrainian. */
    UK,
    /** English. */
    EN;

    /**
     * The value of the {@code lang} parameter.
     *
     * @return the language code
     */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The locale of the labels.
     *
     * @return the locale
     */
    public Locale locale() {
        return Locale.forLanguageTag(code());
    }

    /**
     * Picks the name in this language.
     *
     * @param name   the English name
     * @param nameUk the Ukrainian name, or null
     * @return the Ukrainian name when this is Ukrainian and one exists, otherwise the English name
     */
    public String name(String name, String nameUk) {
        return this == UK && nameUk != null && !nameUk.isBlank() ? nameUk : name;
    }

    /**
     * Reads the {@code lang} parameter.
     *
     * @param value the parameter
     * @return the language
     * @throws ApiProblemException 400 {@code invalid-parameter} naming {@code lang}
     */
    public static ReportLanguage of(String value) {
        return Arrays.stream(values())
            .filter(language -> language.code().equals(value))
            .findFirst()
            .orElseThrow(() -> new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-parameter",
                "The language must be uk or en", Map.of("parameter", "lang")));
    }
}
