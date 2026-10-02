package ua.edu.chnu.awards.document.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import ua.edu.chnu.awards.config.DocumentProperties;

import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

class ObjectStorageTest {

    private static final byte[] PDF = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);

    private final S3Client unreachable = S3Client.builder().endpointOverride(URI.create("http://127.0.0.1:1"))
        .region(Region.US_EAST_1).credentialsProvider(AnonymousCredentialsProvider.create()).forcePathStyle(true)
        .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(5))).build();
    private final ObjectStorage storage = new ObjectStorage(unreachable, new DocumentProperties("award-documents",
        DataSize.ofMegabytes(10), 10, Duration.ofHours(24), null));

    @AfterEach
    void close() {
        unreachable.close();
    }

    @Test
    void ac1_11_anUnreachableStorageIsUnavailableForEveryCall() {
        MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", PDF);

        assertThatThrownBy(() -> storage.put("awards/1/x", file, PDF.length, "application/pdf"))
            .isInstanceOf(StorageUnavailableException.class);
        assertThatThrownBy(() -> storage.get("awards/1/x")).isInstanceOf(StorageUnavailableException.class);
        assertThatThrownBy(() -> storage.delete(List.of("awards/1/x")))
            .isInstanceOf(StorageUnavailableException.class);
        assertThatThrownBy(() -> storage.keysOlderThan("awards/", Instant.now()))
            .isInstanceOf(StorageUnavailableException.class);
    }

    @Test
    void ac1_12_aStorageThatIsDownAtStartIsOnlyLogged() {
        assertThatCode(storage::prepareBucket).doesNotThrowAnyException();
    }
}
