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
                "--connect-timeout-ms", "1000"
        });
        assertEquals(ConnectionProbe.EXIT_WORKDIR, code);
    }

    @Test
    public void helpReturnsUsageExitCode() {
        int code = ConnectionProbe.run(new String[]{"--help"});
        assertEquals(ConnectionProbe.EXIT_USAGE, code);
    }

    @Test
    public void badConfigReturnsExitCode() {
        int code = ConnectionProbe.run(new String[]{"--connect-timeout-ms", "1"});
        assertEquals(ConnectionProbe.EXIT_CONFIG, code);
    }

    @Test
    public void logDirCreateFailureReturnsExitCode() throws Exception {
        Path blocker = Files.createTempFile("probe-log-blocker", ".tmp");
        // Use a file path as --log-dir so createDirectories fails.
        int code = ConnectionProbe.run(new String[]{
                "--log-dir", blocker.toString(),
                "--connect-timeout-ms", "1000"
        });
        assertEquals(ConnectionProbe.EXIT_LOG_DIR, code);
        assertTrue(Files.isRegularFile(blocker));
    }
}
