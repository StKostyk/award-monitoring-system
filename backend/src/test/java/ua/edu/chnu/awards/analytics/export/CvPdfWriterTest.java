package ua.edu.chnu.awards.analytics.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static ua.edu.chnu.awards.analytics.export.UnitAnalyticsFixture.messages;

import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class CvPdfWriterTest {

    private final CvPdfWriter writer = new CvPdfWriter(engine());

    @Test
    void ac4_3_thePdfIsAnA4PageWithTheYearsAndAwardsInUkrainian() throws IOException {
        CvReport report = new CvReport(ReportLanguage.UK, "Нагороди: Олена Коваль", "Сформовано 17.11.2026",
            List.of(new CvReport.Year(2025, List.of(
                    new CvReport.Entry("Державна премія", "Національний", "Кабінет Міністрів", "03.06.2025"),
                    new CvReport.Entry("Подяка", "Факультетський", "", "10.01.2025"))),
                new CvReport.Year(2019, List.of(new CvReport.Entry("Грамота", "Місцевий", "МОН", "01.09.2019")))),
            "Затверджених нагород поки немає.");

        try (PDDocument pdf = Loader.loadPDF(writer.write(report))) {
            String text = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");

            PDRectangle page = pdf.getPage(0).getMediaBox();
            assertThat(page.getWidth()).isCloseTo(PDRectangle.A4.getWidth(), offset(1f));
            assertThat(pdf.getDocumentInformation().getTitle()).isEqualTo(report.title());
            assertThat(text).contains("Нагороди: Олена Коваль", "Сформовано 17.11.2026", "2025 Державна премія",
                "Національний · Кабінет Міністрів · 03.06.2025", "Факультетський · 10.01.2025", "2019 Грамота",
                "Сторінка 1 з 1").doesNotContain("Затверджених нагород");
            assertThat(text.indexOf("2025")).isLessThan(text.indexOf("2019"));
        }
    }

    @Test
    void ac4_5_withoutAwardsThePdfIsOnePageWithTheNote() throws IOException {
        CvReport report = new CvReport(ReportLanguage.EN, "Awards: Olena Koval", "Generated on 17 Nov 2026",
            List.of(), "No approved awards yet.");

        try (PDDocument pdf = Loader.loadPDF(writer.write(report))) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(pdf)).contains("Awards: Olena Koval", "No approved awards yet.",
                "Page 1 of 1");
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
