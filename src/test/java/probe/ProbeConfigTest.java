package probe;

import org.junit.Test;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ProbeConfigTest {

    @Test
    public void parsesDefaults() {
        ProbeConfig config = ProbeConfig.parse(new String[0]);
        assertEquals(Paths.get("logs"), config.getLogDirectory());
        assertNull(config.getWorkingDirectory());
        assertEquals(ProbeConfig.DEFAULT_CONNECT_TIMEOUT_MS, config.getConnectTimeoutMs());
        assertEquals(ProbeConfig.DEFAULT_STATUS_INTERVAL_MS, config.getStatusIntervalMs());
    }

    @Test
    public void parsesExplicitValues() {
        ProbeConfig config = ProbeConfig.parse(new String[]{
                "--log-dir", "out/logs",
                "--workdir", "C:\\Games\\StarCraft",
                "--connect-timeout-ms", "5000",
                "--status-interval-ms", "250"
        });
        assertEquals(Paths.get("out/logs"), config.getLogDirectory());
        assertEquals(Paths.get("C:\\Games\\StarCraft"), config.getWorkingDirectory());
        assertEquals(5000L, config.getConnectTimeoutMs());
        assertEquals(250L, config.getStatusIntervalMs());
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
            assertTrue(ex.getMessage().contains("connect-timeout-ms"));
        }
    }

    @Test
    public void rejectsOutOfRangeStatusInterval() {
        try {
            ProbeConfig.parse(new String[]{"--status-interval-ms", "1"});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("status-interval-ms"));
        }
    }

    @Test
    public void validateRangesReportsBoth() {
        List<String> errors = ProbeConfig.validateRanges(1L, 1L);
        assertEquals(2, errors.size());
    }

    @Test
    public void helpRequested() {
        try {
            ProbeConfig.parse(new String[]{"--help"});
            fail("expected HelpRequestedException");
        } catch (ProbeConfig.HelpRequestedException ex) {
            assertTrue(ex.getMessage().contains("Usage"));
        }
    }
}
