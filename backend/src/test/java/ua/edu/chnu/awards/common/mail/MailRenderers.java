package ua.edu.chnu.awards.common.mail;

import java.nio.charset.StandardCharsets;

import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Mail renderers for unit tests, with the application templates and messages.
 */
final class MailRenderers {

    static final String FRONTEND = "http://localhost:4200";
    static final String FROM = "awards@chnu.edu.ua";

    private MailRenderers() {
    }

    static MailHtmlRenderer renderer(String brand) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messages.setFallbackToSystemLocale(false);
        return new MailHtmlRenderer(engine, messages, FRONTEND, FROM, brand);
    }
}
