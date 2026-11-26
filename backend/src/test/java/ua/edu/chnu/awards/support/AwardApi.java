package ua.edu.chnu.awards.support;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * Award requests for functional tests: complete drafts and submissions through the public API.
 */
public final class AwardApi {

    public static final String AWARDS = "/api/v1/awards";
    public static final long MINISTRY_CATEGORY = 13L;
    public static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");

    private AwardApi() {
    }

    /**
     * Creates a draft with every field a submission needs; the given fields (a title at least) override the
     * defaults: the ministry category, awarding organisation «МОН України», dated a year ago on the Kyiv calendar.
     *
     * @param token  the owner's access token
     * @param fields title or titleUk plus any overrides
     * @return the award id
     */
    public static long complete(String token, Map<String, Object> fields) {
        Map<String, Object> body = new HashMap<>(Map.of("categoryId", MINISTRY_CATEGORY,
            "awardingOrganization", "МОН України", "awardDate", LocalDate.now(KYIV).minusYears(1).toString()));
        body.putAll(fields);
        return AbstractFunctionalTest.as(token).contentType(ContentType.JSON).body(body).post(AWARDS)
            .then().statusCode(HttpStatus.CREATED.value()).extract().jsonPath().getLong("id");
    }

    /**
     * Creates a complete draft and submits it, confirming a possible duplicate.
     *
     * @param token  the owner's access token
     * @param fields title or titleUk plus any overrides
     * @return the award id
     */
    public static long submitted(String token, Map<String, Object> fields) {
        long id = complete(token, fields);
        submit(token, id, version(token, id), true).then().statusCode(HttpStatus.OK.value());
        return id;
    }

    /**
     * Submits an award.
     *
     * @param token                the owner's access token
     * @param id                   the award
     * @param version              the version last read
     * @param acknowledgeDuplicate whether a possible duplicate is confirmed, null to leave it out
     * @return the response
     */
    public static Response submit(String token, long id, long version, Boolean acknowledgeDuplicate) {
        Map<String, Object> body = new HashMap<>();
        body.put("version", version);
        body.put("acknowledgeDuplicate", acknowledgeDuplicate);
        return AbstractFunctionalTest.as(token).contentType(ContentType.JSON).body(body)
            .post(AWARDS + "/" + id + "/submit");
    }

    /**
     * The current version of an award.
     *
     * @param token a token that may read it
     * @param id    the award
     * @return the version
     */
    public static long version(String token, long id) {
        return AbstractFunctionalTest.as(token).get(AWARDS + "/" + id).then().statusCode(HttpStatus.OK.value())
            .extract().jsonPath().getLong("version");
    }
}
