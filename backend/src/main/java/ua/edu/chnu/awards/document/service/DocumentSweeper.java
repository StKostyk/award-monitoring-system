package ua.edu.chnu.awards.document.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.config.DocumentProperties;
import ua.edu.chnu.awards.document.repository.DocumentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Removes document objects that no row refers to, such as the leftovers of a failed cleanup. Young objects are
 * kept, since their upload may still be committing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentSweeper {

    private static final int BATCH = 500;

    private final ObjectStorage storage;
    private final DocumentRepository documents;
    private final DocumentProperties properties;
    private final Clock clock;

    /**
     * One sweep, on {@code app.documents.sweep-cron} (daily at 03:30 by default).
     *
     * @return how many objects were removed
     */
    @Scheduled(cron = "${app.documents.sweep-cron:0 30 3 * * *}")
    public int sweep() {
        Instant cutoff = clock.instant().minus(properties.sweepAge());
        try {
            List<String> orphans = orphans(storage.keysOlderThan(DocumentUpload.KEY_PREFIX, cutoff));
            storage.delete(orphans);
            log.info("Document sweep removed {} orphaned objects", orphans.size());
            return orphans.size();
        } catch (StorageUnavailableException exception) {
            log.warn("Document sweep skipped: {}", exception.getCause().getMessage());
            return 0;
        }
    }

    private List<String> orphans(List<String> keys) {
        List<String> orphans = new ArrayList<>();
        for (int from = 0; from < keys.size(); from += BATCH) {
            List<String> batch = keys.subList(from, Math.min(from + BATCH, keys.size()));
            Set<String> known = new HashSet<>(documents.existingKeys(batch));
            batch.stream().filter(key -> !known.contains(key)).forEach(orphans::add);
        }
        return orphans;
    }
}
