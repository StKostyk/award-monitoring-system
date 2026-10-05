package ua.edu.chnu.awards.document.service;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.config.DocumentProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Client of the {@code clamd} daemon over TCP: {@code INSTREAM} sends the content in length-prefixed chunks and
 * reads one verdict, {@code PING} checks that the daemon answers. One connection per call; the whole scan,
 * sending included, is bounded by the configured timeout, after which the connection is closed.
 */
@Component
@Slf4j
public class ClamAvScanner {

    private static final int CHUNK_SIZE = 8192;
    private static final int MAX_REPLY_LENGTH = 1024;
    private static final String STREAM_PREFIX = "stream: ";
    private static final String FOUND_SUFFIX = " FOUND";
    private static final String CLEAN = "OK";

    private final DocumentProperties.Scan properties;

    /**
     * Creates the client.
     *
     * @param properties the document settings with the scanner's address and timeouts
     */
    public ClamAvScanner(DocumentProperties properties) {
        this.properties = properties.scan();
    }

    /**
     * Scans the content.
     *
     * @param content the file, read to its end
     * @return the name of the signature found, empty when the file is clean or scanning is switched off
     * @throws ScannerUnavailableException when the scanner cannot be reached, does not answer in time or reports
     *                                     an error
     */
    public Optional<String> scan(InputStream content) {
        if (!properties.enabled()) {
            return Optional.empty();
        }
        String reply = call("zINSTREAM", properties.timeout(), out -> {
            byte[] chunk = new byte[CHUNK_SIZE];
            int read = content.read(chunk);
            while (read >= 0) {
                if (read > 0) {
                    out.writeInt(read);
                    out.write(chunk, 0, read);
                }
                read = content.read(chunk);
            }
            out.writeInt(0);
        });
        String verdict = reply.startsWith(STREAM_PREFIX) ? reply.substring(STREAM_PREFIX.length()) : reply;
        if (CLEAN.equals(verdict)) {
            return Optional.empty();
        }
        if (verdict.endsWith(FOUND_SUFFIX)) {
            return Optional.of(verdict.substring(0, verdict.length() - FOUND_SUFFIX.length()));
        }
        throw new ScannerUnavailableException(reply);
    }

    /**
     * Checks that the scanner answers.
     *
     * @return true when {@code clamd} answered {@code PONG} within the connect timeout, which keeps health
     *         checks short while the scanner hangs
     */
    public boolean ping() {
        try {
            return "PONG".equals(call("zPING", properties.connectTimeout(), out -> { }));
        } catch (ScannerUnavailableException unavailable) {
            return false;
        }
    }

    private String call(String command, Duration timeout, Body body) {
        try (Socket socket = new Socket()) {
            Thread deadline = Thread.ofVirtual().start(() -> closeAfter(socket, timeout));
            try {
                socket.connect(new InetSocketAddress(properties.host(), properties.port()),
                    (int) properties.connectTimeout().toMillis());
                socket.setSoTimeout((int) timeout.toMillis());
                try (DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(socket.getOutputStream()))) {
                    out.write((command + "\0").getBytes(StandardCharsets.US_ASCII));
                    body.write(out);
                    out.flush();
                    return readReply(socket.getInputStream());
                }
            } finally {
                deadline.interrupt();
            }
        } catch (IOException exception) {
            throw new ScannerUnavailableException(exception);
        }
    }

    private static String readReply(InputStream in) throws IOException {
        ByteArrayOutputStream reply = new ByteArrayOutputStream();
        int next = in.read();
        while (next > 0 && reply.size() < MAX_REPLY_LENGTH) {
            reply.write(next);
            next = in.read();
        }
        if (next < 0 && reply.size() == 0) {
            throw new IOException("The scanner closed the connection without a reply");
        }
        return reply.toString(StandardCharsets.US_ASCII).strip();
    }

    private static void closeAfter(Socket socket, Duration timeout) {
        try {
            Thread.sleep(timeout);
            socket.close();
        } catch (InterruptedException finished) {
            Thread.currentThread().interrupt();
        } catch (IOException closeFailed) {
            log.debug("The scanner connection could not be closed at its deadline: {}", closeFailed.getMessage());
        }
    }

    @FunctionalInterface
    private interface Body {

        void write(DataOutputStream out) throws IOException;
    }
}
