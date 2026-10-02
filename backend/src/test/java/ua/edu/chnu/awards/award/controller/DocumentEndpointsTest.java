package ua.edu.chnu.awards.award.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ua.edu.chnu.awards.award.dto.UserRef;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.InvalidParameterProblems;
import ua.edu.chnu.awards.document.controller.DocumentController;
import ua.edu.chnu.awards.document.dto.DocumentDownload;
import ua.edu.chnu.awards.document.dto.DocumentResponse;
import ua.edu.chnu.awards.document.entity.DocumentType;
import ua.edu.chnu.awards.document.service.DocumentNotFoundException;
import ua.edu.chnu.awards.document.service.DocumentService;
import ua.edu.chnu.awards.document.service.DocumentUpload;

@WebMvcTest(DocumentController.class)
@Import(InvalidParameterProblems.class)
class DocumentEndpointsTest extends AbstractAwardEndpointsTest {

    private static final String UPLOAD = "/api/v1/awards/5/documents";
    private static final String DOCUMENT = "/api/v1/documents/7";
    private static final String TYPE = "$.type";
    private static final String PROBLEM = "urn:awards:problem:";
    private static final byte[] PDF = "%PDF-1.7 scan".getBytes(StandardCharsets.US_ASCII);
    private static final String NAME = "диплом.pdf";

    @MockitoBean
    private DocumentService documentService;

    @MockitoBean
    private DocumentUpload documentUpload;

    @Test
    void ac1_1_anUploadAnswers201WithTheDocumentAndItsLocation() throws Exception {
        when(documentUpload.upload(eq(5L), any(), eq(DocumentType.CERTIFICATE), eq("Скан")))
            .thenReturn(document());

        mockMvc.perform(multipart(UPLOAD).file(file()).param("type", "CERTIFICATE").param("description", "Скан")
                .with(employee()))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", DOCUMENT))
            .andExpect(jsonPath("$.id").value(7))
            .andExpect(jsonPath("$.awardId").value(5))
            .andExpect(jsonPath("$.fileName").value(NAME))
            .andExpect(jsonPath("$.mimeType").value("application/pdf"))
            .andExpect(jsonPath("$.uploadedBy.id").value(21));
    }

    @Test
    void ac1_6_aMissingOrUnknownTypeAndAMissingFileAnswer400() throws Exception {
        mockMvc.perform(multipart(UPLOAD).file(file()).with(employee()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath(TYPE).value(PROBLEM + "missing-parameter"))
            .andExpect(jsonPath("$.parameter").value("type"));
        mockMvc.perform(multipart(UPLOAD).file(file()).param("type", "PASSPORT").with(employee()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath(TYPE).value(PROBLEM + "invalid-parameter"));
        mockMvc.perform(multipart(UPLOAD).param("type", "PHOTO").with(employee()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.parameter").value("file"));
        verifyNoInteractions(documentUpload);
    }

    @Test
    void ac1_2_refusalsOfTheServiceAreProblemDetailsWithTheirCode() throws Exception {
        when(documentUpload.upload(anyLong(), any(), any(), isNull())).thenThrow(new ApiProblemException(
            HttpStatus.PAYLOAD_TOO_LARGE, "file-too-large", "The file is larger than the limit"));

        mockMvc.perform(multipart(UPLOAD).file(file()).param("type", "PHOTO").with(employee()))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath(TYPE).value(PROBLEM + "file-too-large"));
    }

    @Test
    void ac1_4_anAdministratorWithoutUpdateRightsIsRefusedAndANonNumericIdIs400() throws Exception {
        mockMvc.perform(multipart(UPLOAD).file(file()).param("type", "PHOTO").with(administrator()))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/documents/abc").with(employee()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath(TYPE).value(PROBLEM + "invalid-parameter"));
        verifyNoInteractions(documentUpload, documentService);
    }

    @Test
    void ac1_7_theListComesFromTheService() throws Exception {
        when(documentService.list(5L)).thenReturn(List.of(document()));

        mockMvc.perform(get(UPLOAD).with(employee()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].fileName").value(NAME));
    }

    @Test
    void ac1_8_aDownloadIsAnUncachedSandboxedAttachmentNamedInUtf8() throws Exception {
        when(documentService.open(7L)).thenReturn(new DocumentDownload(NAME, "application/pdf", PDF.length,
            new ByteArrayInputStream(PDF)));

        mockMvc.perform(get(DOCUMENT).with(employee()))
            .andExpect(status().isOk())
            .andExpect(content().bytes(PDF))
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(header().longValue("Content-Length", PDF.length))
            .andExpect(header().string("Content-Disposition",
                "attachment; filename*=UTF-8''%D0%B4%D0%B8%D0%BF%D0%BB%D0%BE%D0%BC.pdf"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Cache-Control", "private, no-store"))
            .andExpect(header().string("Content-Security-Policy", "sandbox"));
    }

    @Test
    void ac1_8_anUnreadableDocumentIs404() throws Exception {
        when(documentService.open(7L)).thenThrow(new DocumentNotFoundException(7L));

        mockMvc.perform(get(DOCUMENT).with(employee()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath(TYPE).value(PROBLEM + "document-not-found"));
    }

    @Test
    void ac1_9_deletingAnswers204() throws Exception {
        mockMvc.perform(delete(DOCUMENT).with(employee())).andExpect(status().isNoContent());

        verify(documentService).delete(7L);
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", NAME, "application/octet-stream", PDF);
    }

    private static DocumentResponse document() {
        return new DocumentResponse(7L, 5L, NAME, DocumentType.CERTIFICATE, "application/pdf", PDF.length, "Скан",
            Instant.parse("2026-10-02T09:00:00Z"), new UserRef(21L, "Анастасія Коваль", "employee.fmi@chnu.edu.ua"));
    }
}
