package ua.edu.chnu.awards.common.mail;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Renders the HTML part of a mail from its plain text with the one brand layout {@code templates/mail/layout.html}:
 * blank lines separate paragraphs, a {@code ---} line becomes a rule, every link is clickable and the first link
 * into the application becomes a button.
 */
@Component
public class MailHtmlRenderer {

    static final String TEMPLATE = "mail/layout";
    static final String NEUTRAL = "neutral";

    private static final Locale UK = Locale.forLanguageTag("uk");
    private static final Pattern PARAGRAPHS = Pattern.compile("\\n[ \\t]*\\n");
    private static final Pattern LINK = Pattern.compile("https?://[^\\s<>\"]*[^\\s<>\".,;:!?)]");
    private static final Pattern LABELLED_LINK = Pattern.compile("(?:(.*?):\\s*)?(https?://\\S+)");
    private static final Map<String, Colours> COLOURS = Map.of(
        "chnu", new Colours("#003278", "#0047ab"),
        NEUTRAL, new Colours("#1e3d30", "#2d6a4f"));

    private final ITemplateEngine templates;
    private final MessageSource messages;
    private final String frontendUrl;
    private final URI application;
    private final String from;
    private final String brand;

    /**
     * Creates the renderer.
     *
     * @param templates   the template engine
     * @param messages    the messages with the brand names and footer lines
     * @param frontendUrl the address of the browser application
     * @param from        the sender address shown in the footer
     * @param brand       the configured brand id; an unknown id renders the neutral brand
     */
    public MailHtmlRenderer(ITemplateEngine templates, MessageSource messages,
                            @Value("${app.auth.frontend-url}") String frontendUrl,
                            @Value("${app.mail.from}") String from,
                            @Value("${app.brand.id:chnu}") String brand) {
        this.templates = templates;
        this.messages = messages;
        this.frontendUrl = frontendUrl;
        this.application = URI.create(frontendUrl);
        this.from = from;
        this.brand = COLOURS.containsKey(brand) ? brand : NEUTRAL;
    }

    /**
     * Renders the HTML part.
     *
     * @param subject the subject line, used as the document title
     * @param content the plain-text body and the footer rule
     * @return the HTML document
     */
    public String render(String subject, MailContent content) {
        Context context = new Context(UK);
        context.setVariable("subject", subject);
        context.setVariable("colours", COLOURS.get(brand));
        context.setVariable("organizationUk", message("brand." + brand + ".organization", UK));
        context.setVariable("organizationEn", message("brand." + brand + ".organization", Locale.ENGLISH));
        context.setVariable("senderUk", sender(UK));
        context.setVariable("senderEn", sender(Locale.ENGLISH));
        context.setVariable("blocks", blocks(content.text()));
        if (content.preferencesLink()) {
            context.setVariable("preferencesUrl", frontendUrl + "/profile#notifications");
            context.setVariable("preferencesLabel", message("mail.preferences", UK) + " / "
                + message("mail.preferences", Locale.ENGLISH));
        }
        return templates.process(TEMPLATE, context);
    }

    List<Block> blocks(String text) {
        List<Block> blocks = new ArrayList<>();
        for (String paragraph : PARAGRAPHS.split(text.replace("\r\n", "\n").strip())) {
            if ("---".equals(paragraph.strip())) {
                blocks.add(Block.rule());
            } else {
                addParagraph(blocks, paragraph.strip());
            }
        }
        return blocks;
    }

    private void addParagraph(List<Block> blocks, String paragraph) {
        List<List<Segment>> lines = new ArrayList<>();
        for (String line : paragraph.split("\\n")) {
            Matcher labelled = LABELLED_LINK.matcher(line.strip());
            if (blocks.stream().noneMatch(Block::isButton) && labelled.matches()
                && isApplicationLink(labelled.group(2))) {
                addText(blocks, lines);
                lines = new ArrayList<>();
                String label = labelled.group(1);
                blocks.add(Block.button(label == null || label.isBlank() ? labelled.group(2) : label.strip(),
                    labelled.group(2)));
            } else {
                lines.add(segments(line));
            }
        }
        addText(blocks, lines);
    }

    boolean isApplicationLink(String url) {
        try {
            URI link = new URI(url);
            return link.getRawUserInfo() == null && application.getScheme().equalsIgnoreCase(link.getScheme())
                && application.getHost().equalsIgnoreCase(link.getHost()) && application.getPort() == link.getPort();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static void addText(List<Block> blocks, List<List<Segment>> lines) {
        if (!lines.isEmpty()) {
            blocks.add(Block.text(lines));
        }
    }

    private static List<Segment> segments(String line) {
        List<Segment> segments = new ArrayList<>();
        Matcher link = LINK.matcher(line);
        int start = 0;
        while (link.find()) {
            if (link.start() > start) {
                segments.add(new Segment(line.substring(start, link.start()), null));
            }
            segments.add(new Segment(link.group(), link.group()));
            start = link.end();
        }
        if (start < line.length()) {
            segments.add(new Segment(line.substring(start), null));
        }
        return segments;
    }

    private String sender(Locale locale) {
        return messages.getMessage("mail.sender", new Object[] {message("brand." + brand + ".name", locale), from},
            locale);
    }

    private String message(String key, Locale locale) {
        return messages.getMessage(key, null, locale);
    }

    /**
     * The brand colours of the header and the button.
     *
     * @param header the header background
     * @param button the button background and link colour
     */
    public record Colours(String header, String button) {
    }

    /**
     * One part of the body: a paragraph of lines, a rule or the button.
     *
     * @param kind  {@code text}, {@code rule} or {@code button}
     * @param lines the lines of a paragraph
     * @param label the button label
     * @param href  the button target
     */
    public record Block(String kind, List<List<Segment>> lines, String label, String href) {

        /**
         * Keeps an unmodifiable copy of the lines.
         */
        public Block {
            lines = List.copyOf(lines.stream().<List<Segment>>map(List::copyOf).toList());
        }

        static Block text(List<List<Segment>> lines) {
            return new Block("text", lines, null, null);
        }

        static Block rule() {
            return new Block("rule", List.of(), null, null);
        }

        static Block button(String label, String href) {
            return new Block("button", List.of(), label, href);
        }

        boolean isButton() {
            return "button".equals(kind);
        }
    }

    /**
     * A run of text within a line, a link when {@code href} is set.
     *
     * @param text the text shown
     * @param href the link target, or null
     */
    public record Segment(String text, String href) {
    }
}
