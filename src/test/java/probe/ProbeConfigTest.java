package probe;

import org.junit.Test;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ProbeConfigTest {

    @Test
    public void parsesDefaults() {
        ProbeConfig config = ProbeConfig.parse(new String[0]);
        assertEquals(Paths.get("logs"), config.getLogDirectory());
        assertNull(config.getWorkingDirectory());
        assertEquals(ProbeConfig.DEFAULT_MATCH_START_WAIT_MS, config.getMatchStartWaitMs());
        assertEquals(ProbeConfig.DEFAULT_STATUS_INTERVAL_MS, config.getStatusIntervalMs());
        assertEquals(0L, config.getOverallRunTimeoutMs());
        assertFalse(config.getRunId().isEmpty());
        assertTrue(config.resolveJsonlPath().getFileName().toString().contains(config.getRunId()));
    }

    @Test
    public void parsesExplicitValuesIncludingAliases() {
        ProbeConfig config = ProbeConfig.parse(new String[]{
                "--log-dir", "out/logs",
                "--workdir", "C:\\Games\\StarCraft",
                "--connect-timeout-ms", "5000",
                "--overall-run-timeout-ms", "90000",
                "--status-interval-ms", "250",
                "--run-id", "run-abc"
        });
        assertEquals(Paths.get("out/logs"), config.getLogDirectory());
        assertEquals(Paths.get("C:\\Games\\StarCraft"), config.getWorkingDirectory());
        assertEquals(5000L, config.getMatchStartWaitMs());
        assertEquals(5000L, config.getConnectTimeoutMs());
        assertEquals(90000L, config.getOverallRunTimeoutMs());
        assertEquals(250L, config.getStatusIntervalMs());
        assertEquals("run-abc", config.getRunId());
        assertEquals(Paths.get("out/logs/connection-probe-run-abc.jsonl"), config.resolveJsonlPath());
    }

    @Test
    public void parsesMatchStartWaitName() {
        ProbeConfig config = ProbeConfig.parse(new String[]{"--match-start-wait-ms", "2000"});
        assertEquals(2000L, config.getMatchStartWaitMs());
    }

    @Test
    public void rejectsUnknownArgument() {
        try {
            ProbeConfig.parse(new String[]{"--nope", "1"});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Unknown argument"));
        }
    }

    @Test
    public void rejectsMissingValue() {
        try {
            ProbeConfig.parse(new String[]{"--log-dir"});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Missing value"));
        }
    }

    @Test
    public void rejectsOutOfRangeTimeout() {
        try {
            ProbeConfig.parse(new String[]{"--connect-timeout-ms", "10"});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("match-start-wait-ms")
                    || ex.getMessage().contains("connect-timeout-ms"));
        }
    }

    @Test
    public void rejectsOverallLessThanMatchWait() {
        try {
            ProbeConfig.parse(new String[]{
                    "--match-start-wait-ms", "5000",
                    "--overall-run-timeout-ms", "2000"
            });
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("overall-run-timeout-ms"));
        }
    }

    @Test
    public void validateRangesReportsIssues() {
        List<String> errors = ProbeConfig.validateRanges(1L, 1L, 0L);
        assertEquals(2, errors.size());
    }

    @Test
    public void helpRequested() {
        try {
            ProbeConfig.parse(new String[]{"--help"});
            fail("expected HelpRequestedException");
        } catch (ProbeConfig.HelpRequestedException ex) {
            assertTrue(ex.getMessage().contains("Usage"));
            assertTrue(ex.getMessage().contains("NOT a verified IPC-connect"));
        }
    }
}
