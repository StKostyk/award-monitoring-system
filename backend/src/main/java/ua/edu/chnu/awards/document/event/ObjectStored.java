package ua.edu.chnu.awards.document.event;

/**
 * An object was written for a document whose row is not committed yet; it is removed if the transaction rolls
 * back.
 *
 * @param key the object key
 */
public record ObjectStored(String key) {
}
