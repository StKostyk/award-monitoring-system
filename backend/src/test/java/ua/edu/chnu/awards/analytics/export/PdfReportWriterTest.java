package ua.edu.chnu.awards.analytics.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static ua.edu.chnu.awards.analytics.export.UnitAnalyticsFixture.department;
import static ua.edu.chnu.awards.analytics.export.UnitAnalyticsFixture.faculty;
import static ua.edu.chnu.awards.analytics.export.UnitAnalyticsFixture.messages;
import static ua.edu.chnu.awards.analytics.export.UnitAnalyticsFixture.report;

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class PdfReportWriterTest {

    private static final int MAX_PAGES = 6;

    private final PdfReportWriter writer = new PdfReportWriter(engine());

    @Test
    void ac3_4_theReportIsAnA4DocumentWithUkrainianTextAndPageNumbers() throws IOException {
        UnitReport report = report(faculty(), ReportLanguage.UK);

        try (PDDocument pdf = Loader.loadPDF(writer.write(report))) {
            String text = new PDFTextStripper().getText(pdf);

            assertThat(writer.format()).isEqualTo(ReportFormat.PDF);
            PDRectangle page = pdf.getPage(0).getMediaBox();
            assertThat(page.getWidth()).isCloseTo(PDRectangle.A4.getWidth(), offset(1f));
            assertThat(page.getHeight()).isCloseTo(PDRectangle.A4.getHeight(), offset(1f));
            assertThat(text).contains("Аналітика підрозділу: Факультет математики та інформатики",
                "Порівняння кафедр", "Затверджені нагороди", "Сторінка 1 з " + pdf.getNumberOfPages(),
                "Пізніше відкликання згоди не змінює вже сформованих файлів");
            assertThat(pdf.getDocumentInformation().getTitle()).isEqualTo(report.title());
        }
    }

    @Test
    void ac3_4_theFontIsEmbedded() throws IOException {
        try (PDDocument pdf = Loader.loadPDF(writer.write(report(faculty(), ReportLanguage.EN)))) {
            for (COSName name : pdf.getPage(0).getResources().getFontNames()) {
                PDFont font = pdf.getPage(0).getResources().getFont(name);

                assertThat(font.isEmbedded()).isTrue();
                assertThat(font.getName()).contains("NotoSans");
            }
        }
    }

    @Test
    void ac3_2_everySectionAndTheHeaderAreInTheFile() throws IOException {
        UnitReport report = report(faculty(), ReportLanguage.EN);

        try (PDDocument pdf = Loader.loadPDF(writer.write(report))) {
            String text = new PDFTextStripper().getText(pdf);

            report.sections().forEach(section -> assertThat(text).contains(section.title()));
            report.header().forEach(field -> assertThat(text).contains(field.label(), field.value()));
            assertThat(text).contains("Petrenko Ivan1", "All other organisations");
        }
    }

    @Test
    void ac3_4_aLargeFacultyFitsInSixPages() throws IOException {
        try (PDDocument pdf = Loader.loadPDF(writer.write(report(faculty(12, 25, 10, 10), ReportLanguage.UK)))) {
            assertThat(pdf.getNumberOfPages()).isBetween(1, MAX_PAGES);
        }
    }

    @Test
    void ac3_2_aDepartmentReportShowsTheEmptySections() throws IOException {
        UnitReport report = report(department(), ReportLanguage.EN);

        try (PDDocument pdf = Loader.loadPDF(writer.write(report))) {
            assertThat(new PDFTextStripper().getText(pdf)).contains(report.empty(),
                "fewer members than the minimum group");
        }
    }

    private static SpringTemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setTemplateEngineMessageSource(messages());
        return engine;
    }
}
