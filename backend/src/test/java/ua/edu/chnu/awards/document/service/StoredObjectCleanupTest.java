package ua.edu.chnu.awards.document.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.document.event.ObjectStored;
import ua.edu.chnu.awards.document.event.ObjectsReleased;

class StoredObjectCleanupTest {

    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final StoredObjectCleanup cleanup = new StoredObjectCleanup(storage);

    @Test
    void ac1_9_committedDeletionsRemoveTheirObjects() {
        cleanup.released(new ObjectsReleased(List.of("awards/5/a", "awards/5/b")));

        verify(storage).delete(List.of("awards/5/a", "awards/5/b"));
    }

    @Test
    void ac1_11_anUploadThatRolledBackRemovesItsObject() {
        cleanup.abandoned(new ObjectStored("awards/5/c"));

        verify(storage).delete(List.of("awards/5/c"));
    }

    @Test
    void ac1_11_aStorageFailureLeavesTheObjectForTheSweep() {
        doThrow(new StorageUnavailableException(new IllegalStateException("down"))).when(storage).delete(anyList());

        assertThatCode(() -> cleanup.abandoned(new ObjectStored("awards/5/c"))).doesNotThrowAnyException();
    }
}
