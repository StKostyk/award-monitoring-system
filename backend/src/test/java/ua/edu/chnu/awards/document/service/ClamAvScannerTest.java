package ua.edu.chnu.awards.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ua.edu.chnu.awards.support.DocumentTestConstants.PDF;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import ua.edu.chnu.awards.config.DocumentProperties;

class ClamAvScannerTest {

    private static final String INSTREAM = "zINSTREAM";
    private static final String PING = "zPING";
    private static final Duration TIMEOUT = Duration.ofMillis(500);
    private static final int MAX_CHUNK = 8192;
    private static final int TEST_SECONDS = 5;

    private ServerSocket server;
    private final CompletableFuture<Received> received = new CompletableFuture<>();
    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void tearDown() throws IOException {
        release.countDown();
        if (server != null) {
            server.close();
        }
    }

    @Test
    void ac3_1_aCleanFileIsStreamedInLengthPrefixedChunksEndingWithAnEmptyOne() throws Exception {
        ClamAvScanner scanner = scannerAnswering(command -> "stream: OK");
        byte[] large = new byte[MAX_CHUNK * 2 + 3];
        System.arraycopy(PDF, 0, large, 0, PDF.length);

        assertThat(scanner.scan(new ByteArrayInputStream(large))).isEmpty();

        Received request = received.get(TEST_SECONDS, TimeUnit.SECONDS);
        assertThat(request.command()).isEqualTo(INSTREAM);
        assertThat(request.content()).isEqualTo(large);
        assertThat(request.chunks()).isGreaterThan(1);
    }

    @Test
    void ac3_1_aFoundSignatureIsReturnedByName() {
        ClamAvScanner scanner = scannerAnswering(command -> "stream: Win.Test.EICAR_HDB-1 FOUND");

        assertThat(scanner.scan(new ByteArrayInputStream(PDF))).contains("Win.Test.EICAR_HDB-1");
    }

    @Test
    void ac3_2_anErrorReplyMeansTheScannerIsUnavailable() {
        ClamAvScanner scanner = scannerAnswering(command -> "INSTREAM size limit exceeded. ERROR");

        assertUnavailable(() -> scanner.scan(new ByteArrayInputStream(PDF)));
    }

    @Test
    void ac3_2_anUnreachableScannerIsUnavailable() throws IOException {
        int port;
        try (ServerSocket closed = new ServerSocket(0)) {
            port = closed.getLocalPort();
        }
        ClamAvScanner scanner = new ClamAvScanner(properties(port, true));

        assertUnavailable(() -> scanner.scan(new ByteArrayInputStream(PDF)));
        assertThat(scanner.ping()).isFalse();
    }

    @Test
    void ac3_2_aScannerThatNeitherReadsNorAnswersTimesOut() throws IOException {
        server = new ServerSocket(0);
        CompletableFuture.runAsync(() -> {
            try (Socket ignored = server.accept()) {
                release.await(TEST_SECONDS, TimeUnit.SECONDS);
            } catch (IOException | InterruptedException ignoredFailure) {
                Thread.currentThread().interrupt();
            }
        });
        ClamAvScanner scanner = new ClamAvScanner(properties(server.getLocalPort(), true));
        byte[] tenMegabytes = new byte[(int) DataSize.ofMegabytes(10).toBytes()];

        long started = System.nanoTime();
        assertUnavailable(() -> scanner.scan(new ByteArrayInputStream(tenMegabytes)));
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(TEST_SECONDS));
    }

    @Test
    void ac3_2_aSilentScannerIsDownWithinTheConnectTimeoutNotTheScanTimeout() throws IOException {
        server = new ServerSocket(0);
        CompletableFuture.runAsync(() -> {
            try (Socket ignored = server.accept()) {
                release.await(TEST_SECONDS, TimeUnit.SECONDS);
            } catch (IOException | InterruptedException ignoredFailure) {
                Thread.currentThread().interrupt();
            }
        });
        ClamAvScanner scanner = new ClamAvScanner(new DocumentProperties("award-documents",
            DataSize.ofMegabytes(10), 10, Duration.ofHours(24), DataSize.ofMegabytes(50), 20, null,
            new DocumentProperties.Scan(true, "localhost", server.getLocalPort(), TIMEOUT,
                Duration.ofSeconds(TEST_SECONDS * 2))));

        long started = System.nanoTime();
        assertThat(scanner.ping()).isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(TEST_SECONDS));
    }

    @Test
    void ac3_2_aPongMeansTheScannerIsUp() throws Exception {
        ClamAvScanner scanner = scannerAnswering(command -> PING.equals(command) ? "PONG" : "UNKNOWN COMMAND");

        assertThat(scanner.ping()).isTrue();
        assertThat(received.get(TEST_SECONDS, TimeUnit.SECONDS).command()).isEqualTo(PING);
    }

    @Test
    void ac3_3_aDisabledScannerPassesEveryFileWithoutAConnection() {
        ClamAvScanner scanner = new ClamAvScanner(properties(1, false));

        assertThat(scanner.scan(new ByteArrayInputStream(PDF))).isEmpty();
    }

    private ClamAvScanner scannerAnswering(Function<String, String> reply) {
        try {
            server = new ServerSocket(0);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        CompletableFuture.runAsync(() -> {
            try (Socket client = server.accept()) {
                DataInputStream in = new DataInputStream(client.getInputStream());
                String command = readCommand(in);
                Received request = INSTREAM.equals(command) ? readChunks(command, in) : new Received(command,
                    new byte[0], 0);
                OutputStream out = client.getOutputStream();
                out.write((reply.apply(command) + "\0").getBytes(StandardCharsets.US_ASCII));
                out.flush();
                received.complete(request);
            } catch (IOException exception) {
                received.completeExceptionally(exception);
            }
        });
        return new ClamAvScanner(properties(server.getLocalPort(), true));
    }

    private static String readCommand(InputStream in) throws IOException {
        ByteArrayOutputStream command = new ByteArrayOutputStream();
        int next = in.read();
        while (next > 0) {
            command.write(next);
            next = in.read();
        }
        return command.toString(StandardCharsets.US_ASCII);
    }

    private static Received readChunks(String command, DataInputStream in) throws IOException {
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        int chunks = 0;
        int length = in.readInt();
        while (length > 0) {
            content.write(in.readNBytes(length));
            chunks++;
            length = in.readInt();
        }
        return new Received(command, content.toByteArray(), chunks);
    }

    private static DocumentProperties properties(int port, boolean enabled) {
        return new DocumentProperties("award-documents", DataSize.ofMegabytes(10), 10, Duration.ofHours(24),
            DataSize.ofMegabytes(50), 20, null,
            new DocumentProperties.Scan(enabled, "localhost", port, TIMEOUT, TIMEOUT));
    }

    private static void assertUnavailable(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOf(ScannerUnavailableException.class)
            .hasFieldOrPropertyWithValue("type", "scanner-unavailable");
    }

    private record Received(String command, byte[] content, int chunks) {
    }
}
