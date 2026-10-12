package ua.edu.chnu.awards.document.dto;

/**
 * The owner's choice to show a document of a shared award to colleagues.
 *
 * @param shownToColleagues true to show it, false to hide it
 */
public record DocumentSharingUpdate(Boolean shownToColleagues) {
}
