package ua.edu.chnu.awards.gdpr.repository;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import ua.edu.chnu.awards.common.retention.RetentionResult;

import lombok.RequiredArgsConstructor;

/**
 * The per-policy results of a retention run as they are stored in {@code retention_runs.results}.
 */
@Component
@RequiredArgsConstructor
public class RetentionResultsJson {

    private static final TypeReference<List<RetentionResult>> RESULT_LIST = new TypeReference<>() {
    };

    private final ObjectMapper mapper;

    /**
     * Reads stored results.
     *
     * @param json the stored JSON
     * @return the results
     */
    public List<RetentionResult> read(String json) {
        try {
            return mapper.readValue(json, RESULT_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Retention results cannot be read", e);
        }
    }

    /**
     * Writes results for storage.
     *
     * @param results the results
     * @return the JSON
     */
    public String write(List<RetentionResult> results) {
        try {
            return mapper.writeValueAsString(results);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Retention results cannot be written", e);
        }
    }
}
