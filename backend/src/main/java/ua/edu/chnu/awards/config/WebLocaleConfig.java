package ua.edu.chnu.awards.config;

import java.time.Duration;
import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

/**
 * Ukrainian by default, English when the browser prefers it or {@code ?lang=en} was used; kept in a cookie.
 */
@Configuration
public class WebLocaleConfig implements WebMvcConfigurer {

    public static final Locale UKRAINIAN = Locale.of("uk");
    private static final Duration COOKIE_AGE = Duration.ofDays(365);

    @Bean
    LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver("lang");
        resolver.setCookieMaxAge(COOKIE_AGE);
        resolver.setRejectInvalidCookies(false);
        resolver.setDefaultLocaleFunction(request -> prefersEnglish(request.getHeader("Accept-Language"))
            ? Locale.ENGLISH : UKRAINIAN);
        return resolver;
    }

    static boolean prefersEnglish(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return false;
        }
        String first = acceptLanguage.split(",")[0].trim().toLowerCase(Locale.ROOT);
        return first.startsWith("en");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        interceptor.setIgnoreInvalidLocale(true);
        registry.addInterceptor(interceptor);
    }
}
