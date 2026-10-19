package ua.edu.chnu.awards.gdpr.service;

import org.springframework.stereotype.Service;

import ua.edu.chnu.awards.gdpr.ConsentLookup;
import ua.edu.chnu.awards.gdpr.dto.ConsentType;
import ua.edu.chnu.awards.gdpr.repository.ConsentRecords;

import lombok.RequiredArgsConstructor;

/**
 * Reads the current consent state from the append-only history on every call.
 */
@Service
@RequiredArgsConstructor
public class ConsentState implements ConsentLookup {

    private final ConsentRecords records;

    @Override
    public boolean granted(long userId, ConsentType type) {
        return records.granted(userId, type);
    }
}
