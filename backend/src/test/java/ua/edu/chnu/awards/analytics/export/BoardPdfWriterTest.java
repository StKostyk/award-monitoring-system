package ua.edu.chnu.awards.analytics.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static ua.edu.chnu.awards.analytics.export.ComplianceFixture.compliance;
import static ua.edu.chnu.awards.analytics.export.ComplianceFixture.quietCompliance;
import static ua.edu.chnu.awards.analytics.export.UnitAnalyticsFixture.messages;
import static ua.edu.chnu.awards.analytics.export.UniversityReportFixture.LONG_NAME;
import static ua.edu.chnu.awards.analytics.export.UniversityReportFixture.empty;
import static ua.edu.chnu.awards.analytics.export.UniversityReportFixture.report;
import static ua.edu.chnu.awards.analytics.export.UniversityReportFixture.university;

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import ua.edu.chnu.awards.analytics.dto.TrendProjection;

class BoardPdfWriterTest {

    private static final int MAX_PAGES = 10;
    private static final int TOPIC_PAGES = 8;

    private final BoardPdfWriter writer = new BoardPdfWriter(engine());

    @Test
    void ac3_2_theReportIsAnA4LandscapeDocumentWithOnePagePerTopic() throws IOException {
        BoardReport report = report(university(), compliance(), ReportLanguage.UK);

        try (PDDocument pdf = Loader.loadPDF(writer.write(report))) {
            PDRectangle page = pdf.getPage(0).getMediaBox();
            assertThat(page.getWidth()).isCloseTo(PDRectangle.A4.getHeight(), offset(1f));
            assertThat(page.getHeight()).isCloseTo(PDRectangle.A4.getWidth(), offset(1f));
            assertThat(pdf.getNumberOfPages()).isEqualTo(TOPIC_PAGES);
            assertThat(pdf.getDocumentInformation().getTitle()).isEqualTo(report.title());
            for (BoardReport.Page topic : report.pages()) {
                assertThat(textOf(pdf, report.pages().indexOf(topic) + 2)).contains(topic.title());
            }
        }
    }

    @Test
    void ac3_2_theTitlePageAndPageNumbersAreInUkrainian() throws IOException {
        BoardReport report = report(university(), compliance(), ReportLanguage.UK);

        try (PDDocument pdf = Loader.loadPDF(writer.write(report))) {
            String text = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");

            assertThat(text).contains("Звіт для ради: Чернівецький національний університет імені Юрія Федьковича",
                "Сторінка 1 з " + pdf.getNumberOfPages(), "У звіті нікого не названо");
            report.header().forEach(field -> assertThat(text).contains(field.label(), field.value()));
        }
    }

    @Test
    void ac3_2_everyFigureOfTheModelIsPrinted() throws IOException {
        BoardReport report = report(university(), compliance(), ReportLanguage.EN);

        try (PDDocument pdf = Loader.loadPDF(writer.write(report))) {
            String text = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");

            assertThat(text).contains("Approved awards 120 100 +20 (+20.0 %)",
                "Projected for 2027: about 31 awards, based on 2022–2025.", "Faculty 8 110.0",
                "Privacy notice 1.2 acknowledged 180 of 220", "Some award reviews are overdue.",
                "under half of the university's average per member");
        }
    }

    @Test
    void ac3_3_longFacultyNamesWrapInsideThePage() throws IOException {
        BoardReport report = report(university(), compliance(), ReportLanguage.EN);

        try (PDDocument pdf = Loader.loadPDF(writer.write(report))) {
            String comparison = textOf(pdf, report.pages().indexOf(page(report, "comparison")) + 2);

            assertThat(comparison).doesNotContain(LONG_NAME).contains("Educational and Scientific Institute");
        }
    }

    @Test
    void ac3_2_manyFacultiesStillFitInTenPages() throws IOException {
        BoardReport report = report(university(20, TrendProjection.TOO_FEW_YEARS), compliance(), ReportLanguage.UK);

        try (PDDocument pdf = Loader.loadPDF(writer.write(report))) {
            assertThat(pdf.getNumberOfPages()).isBetween(TOPIC_PAGES, MAX_PAGES);
        }
    }

    @Test
    void ac3_2_anEmptyPeriodShowsTheEmptyLines() throws IOException {
        BoardReport report = report(empty(), quietCompliance(), ReportLanguage.EN);

        try (PDDocument pdf = Loader.loadPDF(writer.write(report))) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("Nothing to show for this period.",
                "Nothing stands out in this period.", "No run yet", "Too few complete years for a projection.");
        }
    }

    @Test
    void ac3_2_theFontIsEmbedded() throws IOException {
        try (PDDocument pdf = Loader.loadPDF(writer.write(report(university(), compliance(), ReportLanguage.UK)))) {
            for (PDPage page : pdf.getPages()) {
                for (COSName name : page.getResources().getFontNames()) {
                    PDFont font = page.getResources().getFont(name);

                    assertThat(font.isEmbedded()).isTrue();
                    assertThat(font.getName()).contains("NotoSans");
                }
            }
        }
    }

    private static BoardReport.Page page(BoardReport report, String key) {
        return report.pages().stream().filter(page -> page.key().equals(key)).findFirst().orElseThrow();
    }

    private static String textOf(PDDocument pdf, int page) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        return stripper.getText(pdf).strip();
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
