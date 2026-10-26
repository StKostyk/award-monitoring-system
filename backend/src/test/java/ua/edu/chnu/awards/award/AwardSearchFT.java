package ua.edu.chnu.awards.award;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;

import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.AwardApi;
import ua.edu.chnu.awards.support.AwardRows;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AwardSearchFT extends AbstractFunctionalTest {

    private static final String OWNER = "ft.search.owner@chnu.edu.ua";
    private static final String COLLEAGUE = "ft.search.colleague@chnu.edu.ua";
    private static final String DELETED = "ft.search.deleted@chnu.edu.ua";
    private static final List<String> ACCOUNTS = List.of(OWNER, COLLEAGUE, DELETED);
    private static final String ACHIEVEMENTS = "/api/v1/achievements";
    private static final String PUBLIC_ACHIEVEMENTS = "/api/v1/public/achievements";
    private static final String CONTENT_ID = "content.id";
    private static final String TOTAL = "totalElements";
    private static final int YEAR = 1987;
    private static final int PAGED_AWARDS = 5;
    private static final int PAGE_SIZE = 2;
    private static final long OTHER_FACULTY_ID = 10L;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long ownerId;
    private long prizeId;
    private long typoTitleId;
    private long titleMatchId;
    private long descriptionMatchId;
    private long privateId;
    private long pendingId;
    private long draftId;
    private long unitAwardId;
    private long deletedOwnersId;
    private final List<Long> pagedIds = new ArrayList<>();

    @BeforeAll
    void createAwards() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        Organization other = organizationRepository.findById(OTHER_FACULTY_ID).orElseThrow();
        ownerId = withRole(OWNER, department, RoleType.EMPLOYEE, department);
        jdbc.update("update users set first_name = 'Оксана', last_name = 'Шукайленко' where user_id = ?", ownerId);
        withRole(COLLEAGUE, other, RoleType.EMPLOYEE, other);
        LocalDate date = LocalDate.of(YEAR, 5, 20);
        prizeId = shared(AwardRows.award(jdbc, ownerId).title("Best Paper Award")
            .titleUk("Премія імені Тараса Шевченка").awardingOrganization("Horizon Europe").awardDate(date)
            .status("APPROVED").insert(), "PUBLIC");
        jdbc.update("update awards set description_uk = 'За п’ять років досліджень' where award_id = ?", prizeId);
        typoTitleId = shared(AwardRows.award(jdbc, ownerId).title("Orizon lab").awardingOrganization("Lab")
            .awardDate(date).status("APPROVED").insert(), "UNIVERSITY");
        titleMatchId = shared(AwardRows.award(jdbc, ownerId).title("Квазар cup").awardingOrganization("Cup")
            .awardDate(date.minusDays(1)).status("APPROVED").insert(), "UNIVERSITY");
        descriptionMatchId = shared(AwardRows.award(jdbc, ownerId).title("Letter").awardingOrganization("Lab")
            .awardDate(date.plusDays(1)).status("APPROVED").insert(), "UNIVERSITY");
        jdbc.update("update awards set description = 'Про квазар' where award_id = ?", descriptionMatchId);
        privateId = AwardRows.award(jdbc, ownerId).title("Horizon private medal").awardingOrganization("Horizon")
            .awardDate(date).status("APPROVED").insert();
        pendingId = AwardRows.award(jdbc, ownerId).title("Horizon pending").awardDate(date).status("PENDING")
            .insert();
        draftId = AwardRows.award(jdbc, ownerId).title(null).titleUk("Чернетка премії").awardDate(date).insert();
        unitAwardId = AwardRows.award(jdbc, ownerId).title("Horizon unit cup").unit(TestUsers.DAI_DEPARTMENT_ID)
            .awardDate(date).status("APPROVED").insert();
        for (int i = 0; i < PAGED_AWARDS; i++) {
            pagedIds.add(AwardRows.award(jdbc, ownerId).title("Пульсар " + i).awardDate(date).insert());
        }
        long deletedId = withRole(DELETED, department, RoleType.EMPLOYEE, department);
        deletedOwnersId = shared(AwardRows.award(jdbc, deletedId).title("Horizon of the gone").awardDate(date)
            .status("APPROVED").insert(), "PUBLIC");
        jdbc.update("update users set account_status = 'DELETED' where user_id = ?", deletedId);
    }

    @AfterAll
    void deleteUsers() {
        jdbc.update("delete from awards where user_id in"
            + " (select user_id from users where email_address like 'ft.search.%')");
        ACCOUNTS.forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @Test
    void ac1_1_theOwnListFindsOwnAwardsInEveryStatusAndKeepsItsFilters() {
        String owner = tokenOf(OWNER);

        assertThat(ids(as(owner), AwardApi.AWARDS, Map.of("q", "премії"))).containsExactlyInAnyOrder(prizeId, draftId);
        assertThat(ids(as(owner), AwardApi.AWARDS, Map.of("q", "horizon")))
            .contains(prizeId, privateId, pendingId, unitAwardId);
        assertThat(ids(as(owner), AwardApi.AWARDS, Map.of("q", "премії", "status", "DRAFT")))
            .containsExactly(draftId);
        assertThat(ids(as(owner), AwardApi.AWARDS, Map.of("q", "премії", "dateTo", "1900-01-01"))).isEmpty();
        assertThat(ids(as(tokenOf(COLLEAGUE)), AwardApi.AWARDS, Map.of("q", "премії")))
            .doesNotContain(prizeId, draftId);
    }

    @Test
    void ac1_1_aShortOrMissingTextLeavesTheListAndItsOrderUnchanged() {
        String owner = tokenOf(OWNER);
        List<Long> unfiltered = ids(as(owner), AwardApi.AWARDS, Map.of("size", "100"));

        assertThat(ids(as(owner), AwardApi.AWARDS, Map.of("q", " a ", "size", "100"))).isEqualTo(unfiltered);
        assertThat(ids(as(owner), AwardApi.AWARDS, Map.of("q", "", "size", "100"))).isEqualTo(unfiltered);
    }

    @Test
    void ac1_2_theAchievementListsKeepTheirRuleWhileSearching() {
        String colleague = tokenOf(COLLEAGUE);

        assertThat(achievements(colleague, Map.of("q", "horizon")))
            .contains(prizeId, unitAwardId).doesNotContain(privateId, pendingId, deletedOwnersId);
        assertThat(publicIds(Map.of("q", "horizon"))).contains(prizeId, unitAwardId)
            .doesNotContain(typoTitleId, privateId, pendingId, deletedOwnersId);
        assertThat(achievements(colleague, Map.of("q", "horizon", "recipient", "UNIT"))).containsExactly(unitAwardId);
        assertThat(achievements(colleague, Map.of("q", "horizon", "unit", TestUsers.FMI_FACULTY_ID)))
            .contains(prizeId, unitAwardId);
        assertThat(achievements(colleague, Map.of("q", "horizon", "unit", OTHER_FACULTY_ID))).isEmpty();
        assertThat(achievements(colleague, Map.of("q", "horizon", "level", "INTERNATIONAL"))).isEmpty();
        assertThat(publicIds(Map.of("q", "horizon", "person", ownerId))).containsExactly(prizeId);
        assertThat(ids(as(colleague), ACHIEVEMENTS, Map.of("q", "horizon", "year", YEAR - 1))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"премія", "премії", "Шевченко", "award", "best pap", "horizon", "пʼять", "п'ять",
        "п’ять", "BEST PAPER"})
    void ac1_3_ukrainianAndEnglishWordsOfEverySearchedTextFindTheAward(String text) {
        assertThat(achievements(tokenOf(COLLEAGUE), Map.of("q", text))).contains(prizeId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Horizn", "Horrizon", "Шевченкo"})
    void ac1_4_typosInTheTitlesAndOrganisationFindTheAward(String text) {
        assertThat(achievements(tokenOf(COLLEAGUE), Map.of("q", text))).contains(prizeId);
    }

    @Test
    void ac1_4_similarTitlesAreSearchedOnlyWhenNoAwardMatchesTheWords() {
        String colleague = tokenOf(COLLEAGUE);

        assertThat(achievements(colleague, Map.of("q", "horizon"))).contains(prizeId).doesNotContain(typoTitleId);
        assertThat(achievements(colleague, Map.of("q", "orizn"))).contains(typoTitleId);
    }

    @Test
    void ac1_5_aTitleMatchRanksAboveADescriptionMatch() {
        List<Long> found = achievements(tokenOf(COLLEAGUE), Map.of("q", "квазар"));

        assertThat(found).containsExactly(titleMatchId, descriptionMatchId);
    }

    @Test
    void ac1_5_pagesCountEveryMatchWithoutRepeatingOrSkipping() {
        String owner = tokenOf(OWNER);
        List<Long> seen = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            JsonPath body = as(owner).queryParams(Map.of("q", "пульсар", "size", PAGE_SIZE, "page", page))
                .get(AwardApi.AWARDS).then().statusCode(HttpStatus.OK.value()).body(TOTAL, equalTo(PAGED_AWARDS))
                .extract().jsonPath();
            seen.addAll(body.getList(CONTENT_ID, Long.class));
        }

        assertThat(seen).containsExactlyInAnyOrderElementsOf(pagedIds);
        as(owner).queryParams(Map.of("q", "пульсар", "size", 1000)).get(AwardApi.AWARDS).then()
            .statusCode(HttpStatus.OK.value()).body("size", equalTo(100));
    }

    @Test
    void ac1_6_ownerNamesAreSearchedOnTheAchievementListsOnly() {
        List<Long> named = achievements(tokenOf(COLLEAGUE), Map.of("q", "шукайлен"));

        assertThat(named).contains(prizeId, typoTitleId, titleMatchId, descriptionMatchId)
            .doesNotContain(privateId, pendingId, unitAwardId);
        assertThat(publicIds(Map.of("q", "Оксана Шукайленко"))).containsExactly(prizeId);
        assertThat(ids(as(tokenOf(COLLEAGUE)), AwardApi.AWARDS, Map.of("q", "шукайлен"))).isEmpty();
        assertThat(ids(as(tokenOf(OWNER)), AwardApi.AWARDS, Map.of("q", "шукайлен"))).isEmpty();
    }

    @Test
    void ac1_7_aTextOver200CharactersIsRefusedOnEveryList() {
        String tooLong = "a".repeat(201);

        refused(as(tokenOf(OWNER)), AwardApi.AWARDS, tooLong);
        refused(as(tokenOf(COLLEAGUE)), ACHIEVEMENTS, tooLong);
        refused(RestAssured.given(), PUBLIC_ACHIEVEMENTS, tooLong);
        as(tokenOf(OWNER)).queryParam("q", "a".repeat(200)).get(AwardApi.AWARDS).then()
            .statusCode(HttpStatus.OK.value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"'; drop table awards; --", "a & b | !c:*", "horizon:* | (x)", "50% _off_ \\",
        "horizon))"})
    void ac1_7_querySyntaxIsReadAsSeparators(String text) {
        as(tokenOf(COLLEAGUE)).queryParams(Map.of("q", text)).get(ACHIEVEMENTS).then()
            .statusCode(HttpStatus.OK.value());
        RestAssured.given().queryParams(Map.of("q", text)).get(PUBLIC_ACHIEVEMENTS).then()
            .statusCode(HttpStatus.OK.value());
        as(tokenOf(OWNER)).queryParams(Map.of("q", text)).get(AwardApi.AWARDS).then()
            .statusCode(HttpStatus.OK.value());
    }

    @Test
    void ac1_7_aTextWithoutLettersOrDigitsFindsNothing() {
        as(tokenOf(OWNER)).queryParam("q", "??").get(AwardApi.AWARDS).then()
            .statusCode(HttpStatus.OK.value()).body(TOTAL, equalTo(0));
        RestAssured.given().queryParam("q", "%%").get(PUBLIC_ACHIEVEMENTS).then()
            .statusCode(HttpStatus.OK.value()).body(TOTAL, equalTo(0));
    }

    @Test
    void ac1_9_anEditedTitleIsFoundByTheNextSearch() {
        String owner = tokenOf(OWNER);
        long id = AwardApi.complete(owner, Map.of("titleUk", "Чернетка гранту"));
        assertThat(ids(as(owner), AwardApi.AWARDS, Map.of("q", "гранту"))).contains(id);

        as(owner).contentType(ContentType.JSON).body(Map.of("titleUk", "Чернетка стипендії",
                "categoryId", AwardApi.MINISTRY_CATEGORY, "awardingOrganization", "МОН України",
                "awardDate", LocalDate.now(AwardApi.KYIV).minusYears(1).toString(),
                "version", AwardApi.version(owner, id)))
            .put(AwardApi.AWARDS + "/" + id).then().statusCode(HttpStatus.OK.value());

        assertThat(ids(as(owner), AwardApi.AWARDS, Map.of("q", "стипендії"))).contains(id);
        assertThat(ids(as(owner), AwardApi.AWARDS, Map.of("q", "гранту"))).doesNotContain(id);
    }

    private long shared(long awardId, String visibility) {
        jdbc.update("update awards set visibility = ? where award_id = ?", visibility, awardId);
        return awardId;
    }

    private List<Long> achievements(String token, Map<String, Object> params) {
        Map<String, Object> withYear = new HashMap<>(params);
        withYear.put("year", YEAR);
        return ids(as(token), ACHIEVEMENTS, withYear);
    }

    private List<Long> publicIds(Map<String, Object> params) {
        Map<String, Object> withYear = new HashMap<>(params);
        withYear.put("year", YEAR);
        return ids(RestAssured.given(), PUBLIC_ACHIEVEMENTS, withYear);
    }

    private static List<Long> ids(RequestSpecification request, String path, Map<String, ?> params) {
        return request.queryParams(params).get(path).then().statusCode(HttpStatus.OK.value()).extract().jsonPath()
            .getList(AwardApi.AWARDS.equals(path) ? CONTENT_ID : "content.awardId", Long.class);
    }

    private static void refused(RequestSpecification request, String path, String text) {
        request.queryParam("q", text).get(path).then().statusCode(HttpStatus.BAD_REQUEST.value())
            .body("type", equalTo("urn:awards:problem:invalid-parameter")).body("parameter", equalTo("q"));
    }
}
