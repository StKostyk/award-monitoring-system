package ua.edu.chnu.awards.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.document.entity.DocumentFormat;

class DocumentContentTest {

    private static final byte[] PDF = bytes("%PDF-1.7\n");
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0x0D};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', '$', 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};
    private static final int LONG_NAME = 300;

    private final DocumentContent content = new DocumentContent();

    @Test
    void ac1_3_theFormatComesFromTheLeadingBytes() {
        assertThat(content.format(PDF)).isEqualTo(DocumentFormat.PDF);
        assertThat(content.format(JPEG)).isEqualTo(DocumentFormat.JPEG);
        assertThat(content.format(PNG)).isEqualTo(DocumentFormat.PNG);
        assertThat(content.format(WEBP)).isEqualTo(DocumentFormat.WEBP);
    }

    @Test
    void ac1_3_otherContentIsAnUnsupportedType() {
        for (byte[] head : new byte[][] {bytes("PK\u0003\u0004word"), bytes("RIFF\u0000\u0000\u0000\u0000AVI "),
            bytes("%PD"), new byte[0], bytes("<html>")}) {
            assertThatThrownBy(() -> content.format(head)).isInstanceOf(ApiProblemException.class)
                .extracting("type").isEqualTo("unsupported-type");
        }
    }

    @Test
    void ac1_1_readingKeepsTheFirstBytesAndTheSha256() {
        byte[] large = Arrays.copyOf(PDF, 20_000);

        DocumentContent.Facts facts = content.read(new ByteArrayInputStream(large));

        assertThat(facts.head()).hasSize(12).startsWith(PDF);
        assertThat(facts.checksum()).hasSize(64).isEqualTo(content.read(new ByteArrayInputStream(large)).checksum());
        assertThat(content.read(new ByteArrayInputStream(bytes("abc"))).checksum())
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(content.read(new ByteArrayInputStream(JPEG)).head()).isEqualTo(JPEG);
    }

    @Test
    void ac1_1_anUnreadableStreamIsAnError() {
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("disk");
            }
        };

        assertThatThrownBy(() -> content.read(broken)).isInstanceOf(UncheckedIOException.class);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "диплом.pdf | PDF | диплом.pdf",
        "SCAN.PDF | PDF | SCAN.PDF",
        "C:\\scans\\..\\диплом.pdf | PDF | диплом.pdf",
        "/home/user/../photo.jpeg | JPEG | photo.jpeg",
        "photo.JPG | JPEG | photo.JPG",
        "scan | PNG | scan.png",
        "scan.docx | PDF | scan.docx.pdf",
        "IMG_2041.heic | JPEG | IMG_2041.heic.jpg",
        "image.webp | WEBP | image.webp",
        "..  | PDF | document.pdf",
        ".hidden.pdf | PDF | hidden.pdf",
        "' ' | WEBP | document.webp"
    })
    void ac1_6_theNameIsTheLastSegmentWithTheExtensionOfTheContent(String original, DocumentFormat format,
                                                                   String expected) {
        assertThat(content.fileName(original, format)).isEqualTo(expected);
    }

    @Test
    void ac1_6_controlAndFormatCharactersAreRemovedAndAMissingNameIsReplaced() {
        assertThat(content.fileName("гра\u0000мо\u202Eта\r\n.pdf", DocumentFormat.PDF)).isEqualTo("грамота.pdf");
        assertThat(content.fileName("\u0001\u0002", DocumentFormat.PNG)).isEqualTo("document.png");
        assertThat(content.fileName(null, DocumentFormat.JPEG)).isEqualTo("document.jpg");
        assertThat(content.fileName("scans/", DocumentFormat.PDF)).isEqualTo("document.pdf");
    }

    @Test
    void ac1_3_anExtensionOfAnotherSupportedFormatIsAMismatch() {
        assertThatThrownBy(() -> content.fileName("scan.pdf", DocumentFormat.PNG))
            .isInstanceOf(ApiProblemException.class)
            .extracting("type").isEqualTo("content-mismatch");
        assertThatThrownBy(() -> content.fileName("photo.webp", DocumentFormat.JPEG))
            .isInstanceOf(ApiProblemException.class);
    }

    @Test
    void ac1_6_aLongNameIsShortenedTo255WithItsExtension() {
        String shortened = content.fileName("д".repeat(LONG_NAME) + ".pdf", DocumentFormat.PDF);
        String appended = content.fileName("a".repeat(LONG_NAME), DocumentFormat.PNG);
        String surrogates = content.fileName("📄".repeat(LONG_NAME / 2) + ".pdf", DocumentFormat.PDF);

        assertThat(shortened).hasSize(DocumentContent.MAX_NAME_LENGTH).endsWith("д.pdf");
        assertThat(appended).hasSize(DocumentContent.MAX_NAME_LENGTH).endsWith("a.png");
        assertThat(surrogates).hasSizeLessThanOrEqualTo(DocumentContent.MAX_NAME_LENGTH).endsWith("📄.pdf");
    }

    @Test
    void formatsKnowTheirExtensions() {
        assertThat(DocumentFormat.isKnownExtension("JPEG")).isTrue();
        assertThat(DocumentFormat.isKnownExtension("gif")).isFalse();
        assertThat(DocumentFormat.WEBP.mimeType()).isEqualTo("image/webp");
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }
}
