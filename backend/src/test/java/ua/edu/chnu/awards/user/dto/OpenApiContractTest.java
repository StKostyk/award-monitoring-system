package ua.edu.chnu.awards.user.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

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

import ua.edu.chnu.awards.award.dto.AwardCategoryResponse;
import ua.edu.chnu.awards.award.dto.AwardForm;
import ua.edu.chnu.awards.award.dto.AwardResponse;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.delegation.dto.DelegationResponse;
import ua.edu.chnu.awards.delegation.entity.DelegationState;
import ua.edu.chnu.awards.user.entity.AccountStatus;

class OpenApiContractTest {

    private static final Path SPEC = Path.of("..", "docs", "api", "openapi.yml");

    @Test
    @SuppressWarnings("unchecked")
    void ac06_userSchemaMatchesTheProfileResponse() throws IOException {
        Map<String, Object> schemas = schemas();
        Map<String, Object> user = (Map<String, Object>) schemas.get("User");
        Map<String, Object> properties = (Map<String, Object>) user.get("properties");
        List<String> dto = Stream.of(UserProfileResponse.class.getRecordComponents())
            .map(RecordComponent::getName).toList();

        assertThat(properties.keySet()).containsExactlyInAnyOrderElementsOf(dto);
        assertThat(property(properties, "id")).containsEntry("format", "int64");
        assertThat(property(properties, "status")).containsEntry("$ref", "#/components/schemas/AccountStatus");
        Map<String, Object> roles = property(properties, "roles");
        assertThat(roles).containsEntry("type", "array");
        assertThat(property(roles, "items")).containsEntry("$ref", "#/components/schemas/RoleAssignment");
        assertThat(property(properties, "organization"))
            .containsEntry("$ref", "#/components/schemas/OrganizationRef");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ac06_accountStatusEnumMatchesTheEntity() throws IOException {
        Map<String, Object> status = (Map<String, Object>) schemas().get("AccountStatus");

        assertThat((List<String>) status.get("enum"))
            .containsExactlyElementsOf(Arrays.stream(AccountStatus.values()).map(Enum::name).toList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void ac35_delegationSchemaMatchesTheResponse() throws IOException {
        Map<String, Object> schemas = schemas();
        Map<String, Object> delegation = (Map<String, Object>) schemas.get("Delegation");
        Map<String, Object> properties = (Map<String, Object>) delegation.get("properties");
        List<String> dto = Stream.of(DelegationResponse.class.getRecordComponents())
            .map(RecordComponent::getName).toList();

        assertThat(properties.keySet()).containsExactlyInAnyOrderElementsOf(dto);
        assertThat(property(properties, "organization"))
            .containsEntry("$ref", "#/components/schemas/OrganizationRef");
        assertThat(property(properties, "delegate")).containsEntry("$ref", "#/components/schemas/UserBrief");
        assertThat(property(properties, "state"))
            .containsEntry("$ref", "#/components/schemas/DelegationState");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ac35_delegationStateEnumMatchesTheEntity() throws IOException {
        Map<String, Object> state = (Map<String, Object>) schemas().get("DelegationState");

        assertThat((List<String>) state.get("enum")).containsExactlyElementsOf(
            Arrays.stream(DelegationState.values()).map(DelegationState::value).toList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void ac05_awardCategorySchemaMatchesTheCatalogueNode() throws IOException {
        Map<String, Object> category = (Map<String, Object>) schemas().get("AwardCategory");
        Map<String, Object> properties = (Map<String, Object>) category.get("properties");
        List<String> dto = Stream.of(AwardCategoryResponse.class.getRecordComponents())
            .map(RecordComponent::getName).toList();

        assertThat(properties.keySet()).containsExactlyInAnyOrderElementsOf(dto);
        assertThat(property(properties, "id")).containsEntry("format", "int64");
        assertThat(property(properties, "level")).containsEntry("$ref", "#/components/schemas/RecognitionLevel");
        assertThat(property(property(properties, "children"), "items"))
            .containsEntry("$ref", "#/components/schemas/AwardCategory");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ac05_levelEnumsMatchTheSchemaOfRecord() throws IOException {
        Map<String, Object> schemas = schemas();

        assertThat((List<String>) ((Map<String, Object>) schemas.get("RecognitionLevel")).get("enum"))
            .containsExactlyElementsOf(Arrays.stream(RecognitionLevel.values()).map(Enum::name).toList());
        assertThat((List<String>) ((Map<String, Object>) schemas.get("ApprovalLevel")).get("enum"))
            .containsExactlyElementsOf(Arrays.stream(ApprovalLevel.values()).map(Enum::name).toList());
        assertThat((List<String>) ((Map<String, Object>) schemas.get("AwardStatus")).get("enum"))
            .containsExactly("DRAFT", "PENDING", "APPROVED", "REJECTED", "ARCHIVED");
        assertThat((List<String>) ((Map<String, Object>) schemas.get("RequestStatus")).get("enum"))
            .containsExactly("SUBMITTED", "IN_REVIEW", "ESCALATED", "APPROVED", "REJECTED", "RETURNED", "EXPIRED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ac05_awardIdsAreIntegersAndTheTitleFollowsTheColumn() throws IOException {
        Map<String, Object> schemas = schemas();
        Map<String, Object> award = property((Map<String, Object>) schemas.get("Award"), "properties");
        Map<String, Object> create = property((Map<String, Object>) schemas.get("AwardCreateRequest"),
            "properties");

        assertThat(property(award, "id")).containsEntry("format", "int64");
        assertThat(award).containsKeys("titleUk", "descriptionUk", "awardingOrganization", "request")
            .doesNotContainKey("issuingOrganization");
        assertThat(property(create, "title")).containsEntry("maxLength", 500);
        assertThat(property(create, "categoryId")).containsEntry("format", "int64");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ac1_1_awardSchemaMatchesTheResponseAndTheForm() throws IOException {
        Map<String, Object> schemas = schemas();
        Map<String, Object> award = property((Map<String, Object>) schemas.get("Award"), "properties");
        Map<String, Object> create = property((Map<String, Object>) schemas.get("AwardCreateRequest"),
            "properties");
        List<String> response = Stream.of(AwardResponse.class.getRecordComponents())
            .map(RecordComponent::getName).toList();
        List<String> form = Stream.of(AwardForm.class.getRecordComponents())
            .map(RecordComponent::getName).filter(name -> !"version".equals(name)).toList();

        assertThat(award.keySet()).containsExactlyInAnyOrderElementsOf(response);
        assertThat(create.keySet()).containsExactlyInAnyOrderElementsOf(form);
        assertThat(property(award, "category")).containsEntry("$ref", "#/components/schemas/AwardCategoryRef");
        assertThat(property(award, "request")).containsEntry("$ref", "#/components/schemas/AwardRequestSummary");
        assertThat(property(property((Map<String, Object>) schemas.get("ProblemDetail"), "properties"), "errors"))
            .extractingByKey("items").asInstanceOf(MAP)
            .extractingByKey("properties").asInstanceOf(MAP)
            .containsKeys("field", "code", "message");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> property(Map<String, Object> node, String name) {
        return (Map<String, Object>) node.get(name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemas() throws IOException {
        try (InputStream in = Files.newInputStream(SPEC)) {
            Map<String, Object> spec = new Yaml().load(in);
            return (Map<String, Object>) ((Map<String, Object>) spec.get("components")).get("schemas");
        }
    }
}
