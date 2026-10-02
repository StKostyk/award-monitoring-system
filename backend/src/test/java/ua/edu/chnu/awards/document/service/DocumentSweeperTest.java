package ua.edu.chnu.awards.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import ua.edu.chnu.awards.config.DocumentProperties;
import ua.edu.chnu.awards.document.repository.DocumentRepository;

class DocumentSweeperTest {

    private static final Instant NOW = Instant.parse("2026-10-02T03:30:00Z");
    private static final Instant CUTOFF = NOW.minus(Duration.ofHours(24));
    private static final int MANY = 1200;

    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final DocumentSweeper sweeper = new DocumentSweeper(storage, documents,
        new DocumentProperties("award-documents", DataSize.ofMegabytes(10), 10, Duration.ofHours(24), null),
        Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void ac1_11_oldObjectsWithoutARowAreRemovedAndTheOthersKept() {
        when(storage.keysOlderThan(DocumentUpload.KEY_PREFIX, CUTOFF))
            .thenReturn(List.of("awards/1/a", "awards/1/b", "awards/999/orphan"));
        when(documents.existingKeys(anyList())).thenReturn(List.of("awards/1/a", "awards/1/b"));

        assertThat(sweeper.sweep()).isEqualTo(1);

        verify(storage).delete(List.of("awards/999/orphan"));
    }

    @Test
    void ac1_11_keysAreCheckedInBatches() {
        List<String> keys = IntStream.range(0, MANY).mapToObj(index -> "awards/9/" + index).toList();
        when(storage.keysOlderThan(DocumentUpload.KEY_PREFIX, CUTOFF)).thenReturn(keys);
        when(documents.existingKeys(anyList())).thenReturn(List.of());

        assertThat(sweeper.sweep()).isEqualTo(MANY);

        verify(documents).existingKeys(keys.subList(0, 500));
        verify(documents).existingKeys(keys.subList(1000, MANY));
    }

    @Test
    void ac1_11_anUnavailableStorageSkipsTheSweep() {
        doThrow(new StorageUnavailableException(new IllegalStateException("down")))
            .when(storage).keysOlderThan(any(), any());

        assertThat(sweeper.sweep()).isZero();

        verify(storage, never()).delete(anyList());
    }
}
