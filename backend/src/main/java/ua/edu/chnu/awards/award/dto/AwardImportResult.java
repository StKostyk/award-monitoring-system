package ua.edu.chnu.awards.award.dto;

/**
 * The outcome of a spreadsheet import.
 *
 * @param imported    awards stored
 * @param peopleAdded people without an account added
 * @param skipped     rows left out because the award was stored already
 */
public record AwardImportResult(int imported, int peopleAdded, int skipped) {
}
