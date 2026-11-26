package ua.edu.chnu.awards.document.event;

import java.util.List;

/**
 * Document rows were deleted; their objects are removed once the deletion is committed.
 *
 * @param keys the object keys
 */
public record ObjectsReleased(List<String> keys) {

    /**
     * Keeps an unmodifiable copy of the keys.
     *
     * @param keys the object keys
     */
    public ObjectsReleased {
        keys = List.copyOf(keys);
    }
}
