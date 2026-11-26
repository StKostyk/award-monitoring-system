package ua.edu.chnu.awards.common.web;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Client facts of an HTTP request: address, user agent and correlation id. The address is the socket peer, or
 * the client the reverse proxy asserted in {@code X-Forwarded-For} when the peer is a trusted proxy (Tomcat's
 * remote-IP valve, {@code server.tomcat.remoteip.internal-proxies}).
 *
 * @param ip             client address as text, null outside a request
 * @param userAgent      user agent trimmed to the audit column, null when absent
 * @param acceptLanguage the {@code Accept-Language} header, null when absent
 * @param correlationId  id set by {@link CorrelationIdFilter}, null outside a request
 */
public record ClientRequest(String ip, String userAgent, String acceptLanguage, UUID correlationId) {

    public static final String CORRELATION_ATTRIBUTE = ClientRequest.class.getName() + ".correlationId";
    public static final int USER_AGENT_LENGTH = 500;
    static final ClientRequest NONE = new ClientRequest(null, null, null, null);
    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:.]*:[0-9a-fA-F:.]*$");

    /**
     * Facts of the request bound to the current thread, or empty facts outside a request.
     *
     * @return the client facts
     */
    public static ClientRequest current() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
            ? from(attributes.getRequest())
            : NONE;
    }

    /**
     * Facts of the given request.
     *
     * @param request the request
     * @return the client facts
     */
    public static ClientRequest from(HttpServletRequest request) {
        String agent = request.getHeader("User-Agent");
        if (agent != null && agent.length() > USER_AGENT_LENGTH) {
            agent = agent.substring(0, USER_AGENT_LENGTH);
        }
        Object correlation = request.getAttribute(CORRELATION_ATTRIBUTE);
        return new ClientRequest(request.getRemoteAddr(), agent, request.getHeader("Accept-Language"),
            correlation instanceof UUID id ? id : null);
    }

    /**
     * The address as an IP literal, or null when absent or not a literal (no name resolution takes place).
     *
     * @return the address
     */
    public InetAddress address() {
        if (ip == null || !(IPV4.matcher(ip).matches() || IPV6.matcher(ip).matches())) {
            return null;
        }
        try {
            return InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
