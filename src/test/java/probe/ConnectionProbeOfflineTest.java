package probe;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Offline in-process checks that do not start BWClient.
 * Passing these does NOT prove Remastered IPC compatibility.
 */
public class ConnectionProbeOfflineTest {

    @Test
    public void badWorkdirReturnsExitCode() throws Exception {
        Path missing = Paths.get("definitely-missing-workdir-" + System.nanoTime());
        assertFalse(Files.exists(missing));
        Path logDir = Files.createTempDirectory("probe-wd");
        int code = ConnectionProbe.run(new String[]{
                "--workdir", missing.toString(),
                "--log-dir", logDir.toString(),
                "--connect-timeout-ms", "1000",
                "--run-id", "offline-wd"
        }, Paths.get("no-such-repo-root-for-pin-skip"));
        assertEquals(ConnectionProbe.EXIT_WORKDIR, code);
    }

    @Test
    public void helpReturnsUsageExitCode() {
        int code = ConnectionProbe.run(new String[]{"--help"}, Paths.get("missing-root"));
        assertEquals(ConnectionProbe.EXIT_USAGE, code);
    }

    @Test
    public void badConfigReturnsExitCode() {
        int code = ConnectionProbe.run(new String[]{"--connect-timeout-ms", "1"}, Paths.get("missing-root"));
        assertEquals(ConnectionProbe.EXIT_CONFIG, code);
    }

    @Test
    public void logDirCreateFailureReturnsExitCode() throws Exception {
        Path blocker = Files.createTempFile("probe-log-blocker", ".tmp");
        int code = ConnectionProbe.run(new String[]{
                "--log-dir", blocker.toString(),
                "--connect-timeout-ms", "1000",
                "--run-id", "offline-log"
        }, Paths.get("missing-root"));
        assertEquals(ConnectionProbe.EXIT_LOG_DIR, code);
        assertTrue(Files.isRegularFile(blocker));
    }

    @Test
    public void classifyFailureDetectsWindowsApiMissing() {
        assertEquals(
                ConnectionProbe.ERROR_WINDOWS_API_UNAVAILABLE,
                ConnectionProbe.classifyFailure(new UnsatisfiedLinkError("Unable to load library 'kernel32'")));
    }

    @Test
    public void classifyFailureDefaultsToNoBridgeOrWaiting() {
        assertEquals(
                ConnectionProbe.ERROR_NO_BRIDGE_OR_WAITING,
                ConnectionProbe.classifyFailure(new RuntimeException("other")));
    }
}
