package ua.edu.chnu.awards.common.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.common.mail.MailHtmlRenderer.Block;
import ua.edu.chnu.awards.common.mail.MailHtmlRenderer.Segment;

class MailHtmlRendererTest {

    private static final String LINK = MailRenderers.FRONTEND + "/awards/21";
    private static final String TEXT = "Вітаємо, Олено!\n\nНагороду «Грамота» затверджено.\n"
        + "Переглянути нагороду: " + LINK + "\n\n---\n\nHello Olena,\n\nThe award was approved.\n"
        + "View the award: " + LINK + "\n";

    private final MailHtmlRenderer renderer = MailRenderers.renderer("chnu");

    @Test
    void ac5_2_splitsParagraphsAndRendersTheFirstApplicationLinkAsAButton() {
        List<Block> blocks = renderer.blocks(TEXT);

        assertThat(blocks).extracting(Block::kind)
            .containsExactly("text", "text", "button", "rule", "text", "text");
        assertThat(blocks.get(2).label()).isEqualTo("Переглянути нагороду");
        assertThat(blocks.get(2).href()).isEqualTo(LINK);
        assertThat(blocks.get(5).lines().get(1))
            .containsExactly(new Segment("View the award: ", null), new Segment(LINK, LINK));
    }

    @Test
    void ac5_2_linksOutsideTheApplicationStayLinksWithoutTrailingPunctuation() {
        List<Block> blocks = renderer.blocks("See https://chnu.edu.ua/rules.\nВідкрити: " + LINK);

        assertThat(blocks).extracting(Block::kind).containsExactly("text", "button");
        assertThat(blocks.get(0).lines().get(0)).containsExactly(new Segment("See ", null),
            new Segment("https://chnu.edu.ua/rules", "https://chnu.edu.ua/rules"), new Segment(".", null));
        assertThat(blocks.get(1).label()).isEqualTo("Відкрити");
    }

    @Test
    void ac5_2_linksToLookAlikeHostsNeverBecomeTheButton() {
        List<Block> blocks = renderer.blocks("Confirm: http://localhost:4200.evil.com/login\n"
            + "Sign in: http://localhost:4200@evil.com/x\nOpen: http://localhost:4201/awards/1\n"
            + "Переглянути нагороду: " + LINK);

        assertThat(blocks).extracting(Block::kind).containsExactly("text", "button");
        assertThat(blocks.get(0).lines()).hasSize(3);
        assertThat(blocks.get(1).href()).isEqualTo(LINK);
        assertThat(renderer.isApplicationLink("http://local host/x")).isFalse();
    }

    @Test
    void ac5_2_aBareApplicationLinkIsItsOwnLabel() {
        assertThat(renderer.blocks(LINK)).singleElement()
            .satisfies(block -> assertThat(block.label()).isEqualTo(LINK));
    }

    @Test
    void ac5_2_rendersTheBrandLayoutWithInlineStylesOnly() {
        String html = renderer.render("Нагороду затверджено / Award approved", MailContent.notification(TEXT));

        assertThat(html).contains("Чернівецький національний університет імені Юрія Федьковича",
            "Yuriy Fedkovych Chernivtsi National University", "background-color: #003278;",
            "<a href=\"" + LINK + "\"", ">Переглянути нагороду</a>", "<hr", "width=\"600\"",
            "Цей лист надіслала система «Облік нагород ЧНУ», awards@chnu.edu.ua.",
            "This message was sent by ChNU Awards, awards@chnu.edu.ua.");
        assertThat(html).doesNotContain("<style", "<img", "<link", "<script", " th:");
    }

    @Test
    void ac5_3_aNotificationLinksToThePreferences() {
        String html = renderer.render("Subject", MailContent.notification(TEXT));

        assertThat(html).contains("href=\"" + MailRenderers.FRONTEND + "/profile#notifications\"",
            "Керувати сповіщеннями / Manage notifications");
    }

    @Test
    void ac5_3_aSecurityMailHasNoPreferencesLink() {
        String html = renderer.render("Subject", MailContent.mandatory(TEXT));

        assertThat(html).doesNotContain("/profile#notifications", "Manage notifications");
    }

    @Test
    void ac5_4_escapesMarkupInTheText() {
        String html = renderer.render("<b>Subject</b>",
            MailContent.notification("Нагорода «<script>alert('x')</script> & \"Prize\"»"));

        assertThat(html).doesNotContain("<script>", "<b>Subject")
            .contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; &quot;Prize&quot;",
                "&lt;b&gt;Subject&lt;/b&gt;");
    }

    @Test
    void anUnknownBrandRendersTheNeutralBrand() {
        String html = MailRenderers.renderer("acme").render("Subject", MailContent.mandatory("Text"));

        assertThat(html).contains("background-color: #1e3d30;", "University", "Award Registry");
    }
}
