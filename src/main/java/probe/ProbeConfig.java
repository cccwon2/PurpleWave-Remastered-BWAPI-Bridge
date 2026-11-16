package probe;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * CLI / script configuration for the read-only connection probe.
 *
 * <p>{@code matchStartWaitMs} is a <b>match-start</b> wait, not a verified IPC-connect wait:
 * public JBWAPI {@code BWEventListener} has no connect callback.
 */
public final class ProbeConfig {
    /** @deprecated name kept as CLI alias; means match-start wait */
    public static final long DEFAULT_MATCH_START_WAIT_MS = 60_000L;
    public static final long DEFAULT_CONNECT_TIMEOUT_MS = DEFAULT_MATCH_START_WAIT_MS;
    public static final long DEFAULT_STATUS_INTERVAL_MS = 1_000L;
    public static final long MIN_MATCH_START_WAIT_MS = 1_000L;
    public static final long MAX_MATCH_START_WAIT_MS = 3_600_000L;
    public static final long MIN_CONNECT_TIMEOUT_MS = MIN_MATCH_START_WAIT_MS;
    public static final long MAX_CONNECT_TIMEOUT_MS = MAX_MATCH_START_WAIT_MS;
    public static final long MIN_STATUS_INTERVAL_MS = 100L;
    public static final long MAX_STATUS_INTERVAL_MS = 60_000L;
    /** 0 = disabled */
    public static final long MIN_OVERALL_RUN_TIMEOUT_MS = 0L;
    public static final long MAX_OVERALL_RUN_TIMEOUT_MS = 86_400_000L;

    private final Path logDirectory;
    private final Path workingDirectory;
    private final long matchStartWaitMs;
    private final long statusIntervalMs;
    private final long overallRunTimeoutMs;
    private final String runId;

    public ProbeConfig(
            Path logDirectory,
            Path workingDirectory,
            long matchStartWaitMs,
            long statusIntervalMs,
            long overallRunTimeoutMs,
            String runId) {
        this.logDirectory = Objects.requireNonNull(logDirectory, "logDirectory");
        this.workingDirectory = workingDirectory;
        this.matchStartWaitMs = matchStartWaitMs;
        this.statusIntervalMs = statusIntervalMs;
        this.overallRunTimeoutMs = overallRunTimeoutMs;
        this.runId = Objects.requireNonNull(runId, "runId");
    }

    public Path getLogDirectory() {
        return logDirectory;
    }

    public Path getWorkingDirectory() {
        return workingDirectory;
    }

    /** Match-start wait (not a pure IPC-connect timeout). */
    public long getMatchStartWaitMs() {
        return matchStartWaitMs;
    }

    /** Alias for {@link #getMatchStartWaitMs()}. */
    public long getConnectTimeoutMs() {
        return matchStartWaitMs;
    }

    public long getStatusIntervalMs() {
        return statusIntervalMs;
    }

    /** 0 means no overall run limit. */
    public long getOverallRunTimeoutMs() {
        return overallRunTimeoutMs;
    }

    public String getRunId() {
        return runId;
    }

    public Path resolveJsonlPath() {
        return logDirectory.resolve("connection-probe-" + runId + ".jsonl");
    }

    public static ProbeConfig parse(String[] args) {
        Path logDirectory = Paths.get("logs");
        Path workingDirectory = null;
        long matchStartWaitMs = DEFAULT_MATCH_START_WAIT_MS;
        long statusIntervalMs = DEFAULT_STATUS_INTERVAL_MS;
        long overallRunTimeoutMs = 0L;
        String runId = null;

        if (args == null) {
            throw new IllegalArgumentException("args must not be null");
        }

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--help".equals(arg) || "-h".equals(arg)) {
                throw new HelpRequestedException(usage());
            }
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + arg);
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for " + arg);
            }
            String value = args[++i];
            switch (arg) {
                case "--log-dir":
                    logDirectory = Paths.get(value);
                    break;
                case "--workdir":
                    workingDirectory = Paths.get(value);
                    break;
                case "--match-start-wait-ms":
                case "--connect-timeout-ms":
                    matchStartWaitMs = parseLong(arg, value);
                    break;
                case "--status-interval-ms":
                    statusIntervalMs = parseLong(arg, value);
                    break;
                case "--overall-run-timeout-ms":
                    overallRunTimeoutMs = parseLong(arg, value);
                    break;
                case "--run-id":
                    if (value.trim().isEmpty()) {
                        throw new IllegalArgumentException("--run-id must be non-empty");
                    }
                    runId = value.trim();
                    break;
                default:
                    throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }

        List<String> errors = validateRanges(matchStartWaitMs, statusIntervalMs, overallRunTimeoutMs);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
        if (runId == null) {
            runId = UUID.randomUUID().toString();
        }
        return new ProbeConfig(
                logDirectory, workingDirectory, matchStartWaitMs, statusIntervalMs, overallRunTimeoutMs, runId);
    }

    public static List<String> validateRanges(
            long matchStartWaitMs, long statusIntervalMs, long overallRunTimeoutMs) {
        List<String> errors = new ArrayList<String>();
        if (matchStartWaitMs < MIN_MATCH_START_WAIT_MS || matchStartWaitMs > MAX_MATCH_START_WAIT_MS) {
            errors.add("match-start-wait-ms/--connect-timeout-ms must be in ["
                    + MIN_MATCH_START_WAIT_MS + ", " + MAX_MATCH_START_WAIT_MS + "]");
        }
        if (statusIntervalMs < MIN_STATUS_INTERVAL_MS || statusIntervalMs > MAX_STATUS_INTERVAL_MS) {
            errors.add("status-interval-ms must be in [" + MIN_STATUS_INTERVAL_MS + ", " + MAX_STATUS_INTERVAL_MS + "]");
        }
        if (overallRunTimeoutMs < MIN_OVERALL_RUN_TIMEOUT_MS
                || overallRunTimeoutMs > MAX_OVERALL_RUN_TIMEOUT_MS) {
            errors.add("overall-run-timeout-ms must be in ["
                    + MIN_OVERALL_RUN_TIMEOUT_MS + ", " + MAX_OVERALL_RUN_TIMEOUT_MS + "] (0 disables)");
        }
        if (overallRunTimeoutMs > 0 && overallRunTimeoutMs < matchStartWaitMs) {
            errors.add("overall-run-timeout-ms when set must be >= match-start-wait-ms");
        }
        return errors;
    }

    /** Backward-compatible overload used by older tests. */
    public static List<String> validateRanges(long connectTimeoutMs, long statusIntervalMs) {
        return validateRanges(connectTimeoutMs, statusIntervalMs, 0L);
    }

    private static long parseLong(String name, String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " must be an integer, got: " + value);
        }
    }

    public static String usage() {
        return "Usage: java -jar connection-probe.jar"
                + " [--log-dir <path>]"
                + " [--workdir <path>]"
                + " [--match-start-wait-ms <ms>]"
                + " [--connect-timeout-ms <ms>]"
                + " [--overall-run-timeout-ms <ms>]"
                + " [--status-interval-ms <ms>]"
                + " [--run-id <id>]"
                + System.lineSeparator()
                + "Defaults: log-dir=logs, match-start-wait-ms=" + DEFAULT_MATCH_START_WAIT_MS
                + ", overall-run-timeout-ms=0 (disabled), status-interval-ms=" + DEFAULT_STATUS_INTERVAL_MS
                + ", run-id=random UUID"
                + System.lineSeparator()
                + "Note: match-start-wait is NOT a verified IPC-connect timeout; public JBWAPI has no connect callback."
                + System.lineSeparator()
                + "Read-only: never issues game commands. supplyUsed/supplyTotal are BWAPI internal"
                + " units (display supply = internal / 2).";
    }

    public static final class HelpRequestedException extends RuntimeException {
        public HelpRequestedException(String message) {
            super(message);
        }
    }
}
