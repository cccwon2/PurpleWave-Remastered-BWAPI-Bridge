package probe;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Process-level offline IT: no StarCraft / no bridge (MOCK / game-less).
 * Success here means timeout/failure reporting works — not Remastered compatibility.
 */
public class ConnectionProbeProcessIT {

    @Test
    public void withoutBridgeReportsFailureOrTimeout() throws Exception {
        Path jar = Paths.get("target", "connection-probe-0.1.0.jar");
        assumeTrue("shaded jar missing; run mvn package/verify", Files.exists(jar));

        Path logDir = Files.createTempDirectory("probe-offline");
        String runId = "it-offline-" + System.nanoTime();
        ProcessResult result = runProbe(jar,
                "--log-dir", logDir.toString(),
                "--match-start-wait-ms", "4000",
                "--status-interval-ms", "500",
                "--run-id", runId);

        assertTrue(
                "expected FAILED or TIMEOUT, got " + result.exitCode + " out=" + result.stdout + " err=" + result.stderr,
                result.exitCode == ConnectionProbe.EXIT_FAILED
                        || result.exitCode == ConnectionProbe.EXIT_TIMEOUT);

        Path jsonl = logDir.resolve("connection-probe-" + runId + ".jsonl");
        assertTrue("jsonl missing. out=" + result.stdout + " err=" + result.stderr, Files.exists(jsonl));
        List<String> lines = Files.readAllLines(jsonl, StandardCharsets.UTF_8);
        assertFalse(lines.isEmpty());
        String joined = String.join("\n", lines);
        assertTrue(joined.contains("PROCESS_STARTED"));
        assertTrue(joined.contains("WAITING_FOR_BRIDGE") || joined.contains("FAILED"));
        assertTrue(joined.contains("\"runId\":\"" + runId + "\""));
        assertFalse("offline run must not claim MATCH_STARTED", joined.contains("\"event\":\"MATCH_STARTED\""));
        // Prefer classified error codes when FAILED path is taken.
        if (result.exitCode == ConnectionProbe.EXIT_FAILED) {
            assertTrue(
                    "expected classified errorCode",
                    joined.contains(ConnectionProbe.ERROR_WINDOWS_API_UNAVAILABLE)
                            || joined.contains(ConnectionProbe.ERROR_NO_BRIDGE_OR_WAITING));
        }
    }

    @Test
    public void runScopedJsonlDoesNotReuseGenericName() throws Exception {
        Path jar = Paths.get("target", "connection-probe-0.1.0.jar");
        assumeTrue(Files.exists(jar));
        Path logDir = Files.createTempDirectory("probe-run-scope");
        String runId = "scope-" + System.nanoTime();
        runProbe(jar,
                "--log-dir", logDir.toString(),
                "--match-start-wait-ms", "2000",
                "--run-id", runId);
        assertTrue(Files.exists(logDir.resolve("connection-probe-" + runId + ".jsonl")));
        assertFalse(Files.exists(logDir.resolve("connection-probe.jsonl")));
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(logDir, "connection-probe-*.jsonl")) {
            boolean found = false;
            for (Path p : stream) {
                if (p.getFileName().toString().contains(runId)) {
                    found = true;
                }
            }
            assertTrue(found);
        }
    }

    private static ProcessResult runProbe(Path jar, String... args) throws Exception {
        String javaHome = System.getProperty("java.home");
        Path javaBin = Paths.get(javaHome, "bin", isWindows() ? "java.exe" : "java");
        ProcessBuilder pb = new ProcessBuilder();
        pb.command().add(javaBin.toAbsolutePath().toString());
        pb.command().add("-jar");
        pb.command().add(jar.toAbsolutePath().toString());
        for (String arg : args) {
            pb.command().add(arg);
        }
        Process process = pb.start();
        StreamGobbler out = new StreamGobbler(process.getInputStream());
        StreamGobbler err = new StreamGobbler(process.getErrorStream());
        out.start();
        err.start();
        boolean finished = process.waitFor(120, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("probe process did not exit in time");
        }
        out.join(5000L);
        err.join(5000L);
        return new ProcessResult(process.exitValue(), out.text(), err.text());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static final class ProcessResult {
        final int exitCode;
        final String stdout;
        final String stderr;

        ProcessResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    private static final class StreamGobbler extends Thread {
        private final InputStream in;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        StreamGobbler(InputStream in) {
            this.in = in;
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                byte[] chunk = new byte[4096];
                int n;
                while ((n = in.read(chunk)) >= 0) {
                    buffer.write(chunk, 0, n);
                }
            } catch (Exception ignored) {
                // process ended
            }
        }

        String text() {
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
