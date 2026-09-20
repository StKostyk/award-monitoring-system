package ua.edu.chnu.awards.support;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.restassured.RestAssured;
import io.restassured.response.Response;

/**
 * Reads messages from the Mailpit container through its HTTP API.
 */
public final class Mailpit {

    private static final Pattern LINK = Pattern.compile("https?://[^\s\"<]+token=[A-Za-z0-9_-]+");

    private static final int OK = 200;
    private static final int PAGE = 200;
    private static final int ATTEMPTS = 60;
    private static final long PAUSE_MS = 250;

    private final String apiUrl;

    public Mailpit(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    /**
     * Deletes every stored message.
     */
    public void clear() {
        RestAssured.given().delete(apiUrl + "/api/v1/messages").then().statusCode(OK);
    }

    /**
     * Messages addressed to the given recipient, newest first.
     *
     * @param recipient email address
     * @return message summaries as returned by Mailpit
     */
    public List<Map<String, Object>> messagesTo(String recipient) {
        Response response = RestAssured.given().queryParam("limit", PAGE).get(apiUrl + "/api/v1/messages");
        response.then().statusCode(OK);
        List<Map<String, Object>> all = response.jsonPath().getList("messages");
        return all.stream().filter(message -> addressedTo(message, recipient)).toList();
    }

    @SuppressWarnings("unchecked")
    private static boolean addressedTo(Map<String, Object> message, String recipient) {
        List<Map<String, Object>> to = (List<Map<String, Object>>) message.get("To");
        return to != null && to.stream().anyMatch(a -> recipient.equalsIgnoreCase(String.valueOf(a.get("Address"))));
    }

    /**
     * Waits briefly for a message to the recipient and returns its plain-text body.
     *
     * @param recipient email address
     * @return text body of the newest message
     */
    public String latestTextTo(String recipient) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            List<Map<String, Object>> messages = messagesTo(recipient);
            if (!messages.isEmpty()) {
                String id = String.valueOf(messages.get(0).get("ID"));
                return RestAssured.given().get(apiUrl + "/api/v1/message/" + id).jsonPath().getString("Text");
            }
            sleep();
        }
        throw new IllegalStateException("No message delivered to " + recipient + "; inbox has "
            + RestAssured.given().get(apiUrl + "/api/v1/messages").jsonPath().getInt("total") + " messages");
    }

    /**
     * The first verification-style link (one carrying a {@code token} parameter) in a message body.
     *
     * @param text message text
     * @return the link
     */
    public static String linkIn(String text) {
        Matcher matcher = LINK.matcher(text);
        if (!matcher.find()) {
            throw new IllegalStateException("No link with a token in: " + text);
        }
        return matcher.group();
    }

    private static void sleep() {
        try {
            Thread.sleep(PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
