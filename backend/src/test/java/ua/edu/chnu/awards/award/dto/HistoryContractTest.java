package ua.edu.chnu.awards.award.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import ua.edu.chnu.awards.audit.dto.AuditTrailEntry;
import ua.edu.chnu.awards.award.entity.AwardSnapshot;
import ua.edu.chnu.awards.award.entity.VersionAction;

class HistoryContractTest {

    private static final Path SPEC = Path.of("..", "docs", "api", "openapi.yml");

    @Test
    void ac1_8_versionSchemasMatchTheResponse() throws IOException {
        Map<String, Object> schemas = schemas();

        assertThat(properties(schemas, "AwardVersion")).containsExactlyInAnyOrderElementsOf(
            names(AwardVersionResponse.class.getRecordComponents()));
        assertThat(properties(schemas, "AwardSnapshot")).containsExactlyInAnyOrderElementsOf(
            names(AwardSnapshot.class.getRecordComponents()));
        assertThat(properties(schemas, "FieldChange")).containsExactlyInAnyOrderElementsOf(
            names(FieldChange.class.getRecordComponents()));
        assertThat(node(schemas, "VersionAction").get("enum")).isEqualTo(
            Arrays.stream(VersionAction.values()).map(Enum::name).toList());
    }

    @Test
    void ac2_13_decisionSchemasMatchTheBodyAndTheOutcome() throws IOException {
        Map<String, Object> schemas = schemas();

        assertThat(properties(schemas, "ReviewDecision")).containsExactlyInAnyOrderElementsOf(
            names(ReviewDecisionRequest.class.getRecordComponents()));
        assertThat(properties(schemas, "DecisionOutcome")).containsExactlyInAnyOrderElementsOf(
            names(DecisionOutcome.class.getRecordComponents()));
    }

    @Test
    void ac1_10_auditTrailSchemaMatchesTheEntry() throws IOException {
        assertThat(properties(schemas(), "AuditTrailEntry")).containsExactlyInAnyOrderElementsOf(
            names(AuditTrailEntry.class.getRecordComponents()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ac1_6_ac1_11_statusSchemasMatchTheView() throws IOException {
        Map<String, Object> schemas = schemas();

        assertThat(properties(schemas, "AwardStatusView")).containsExactlyInAnyOrderElementsOf(
            names(AwardStatusView.class.getRecordComponents()));
        assertThat(properties(schemas, "PathStep")).containsExactlyInAnyOrderElementsOf(
            names(PathStep.class.getRecordComponents()));
        assertThat(properties(schemas, "ReviewDecisionView")).containsExactlyInAnyOrderElementsOf(
            names(DecisionView.class.getRecordComponents()));
        assertThat(properties(schemas, "AwardRequestSummary")).containsExactlyInAnyOrderElementsOf(
            names(RequestSummary.class.getRecordComponents()));
        Map<String, Object> delay = (Map<String, Object>) ((Map<String, Object>) node(schemas, "AwardStatusView")
            .get("properties")).get("delay");
        assertThat(((Map<String, Object>) delay.get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(
            names(StatusDelay.class.getRecordComponents()));
    }

    private static List<String> names(RecordComponent... components) {
        return Stream.of(components).map(RecordComponent::getName).toList();
    }

    @SuppressWarnings("unchecked")
    private static Iterable<String> properties(Map<String, Object> schemas, String name) {
        return ((Map<String, Object>) node(schemas, name).get("properties")).keySet();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> node(Map<String, Object> schemas, String name) {
        return (Map<String, Object>) schemas.get(name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemas() throws IOException {
        try (InputStream in = Files.newInputStream(SPEC)) {
            Map<String, Object> spec = new Yaml().load(in);
            return (Map<String, Object>) ((Map<String, Object>) spec.get("components")).get("schemas");
        }
    }
}
