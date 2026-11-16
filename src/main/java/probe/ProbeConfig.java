package probe;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * CLI / script configuration for the read-only connection probe.
 */
public final class ProbeConfig {
    public static final long DEFAULT_CONNECT_TIMEOUT_MS = 60_000L;
    public static final long DEFAULT_STATUS_INTERVAL_MS = 1_000L;
    public static final long MIN_CONNECT_TIMEOUT_MS = 1_000L;
    public static final long MAX_CONNECT_TIMEOUT_MS = 3_600_000L;
    public static final long MIN_STATUS_INTERVAL_MS = 100L;
    public static final long MAX_STATUS_INTERVAL_MS = 60_000L;

    private final Path logDirectory;
    private final Path workingDirectory;
    private final long connectTimeoutMs;
    private final long statusIntervalMs;

    public ProbeConfig(Path logDirectory, Path workingDirectory, long connectTimeoutMs, long statusIntervalMs) {
        this.logDirectory = Objects.requireNonNull(logDirectory, "logDirectory");
        this.workingDirectory = workingDirectory;
        this.connectTimeoutMs = connectTimeoutMs;
        this.statusIntervalMs = statusIntervalMs;
    }

    public Path getLogDirectory() {
        return logDirectory;
    }

    public Path getWorkingDirectory() {
        return workingDirectory;
    }

    public long getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public long getStatusIntervalMs() {
        return statusIntervalMs;
    }

    public static ProbeConfig parse(String[] args) {
        Path logDirectory = Paths.get("logs");
        Path workingDirectory = null;
        long connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS;
        long statusIntervalMs = DEFAULT_STATUS_INTERVAL_MS;

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
                case "--connect-timeout-ms":
                    connectTimeoutMs = parseLong(arg, value);
                    break;
                case "--status-interval-ms":
                    statusIntervalMs = parseLong(arg, value);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }

        List<String> errors = validateRanges(connectTimeoutMs, statusIntervalMs);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
        return new ProbeConfig(logDirectory, workingDirectory, connectTimeoutMs, statusIntervalMs);
    }

    public static List<String> validateRanges(long connectTimeoutMs, long statusIntervalMs) {
        List<String> errors = new ArrayList<String>();
        if (connectTimeoutMs < MIN_CONNECT_TIMEOUT_MS || connectTimeoutMs > MAX_CONNECT_TIMEOUT_MS) {
            errors.add("connect-timeout-ms must be in [" + MIN_CONNECT_TIMEOUT_MS + ", " + MAX_CONNECT_TIMEOUT_MS + "]");
        }
        if (statusIntervalMs < MIN_STATUS_INTERVAL_MS || statusIntervalMs > MAX_STATUS_INTERVAL_MS) {
            errors.add("status-interval-ms must be in [" + MIN_STATUS_INTERVAL_MS + ", " + MAX_STATUS_INTERVAL_MS + "]");
        }
        return errors;
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
                + " [--connect-timeout-ms <ms>]"
                + " [--status-interval-ms <ms>]"
                + System.lineSeparator()
                + "Defaults: log-dir=logs, connect-timeout-ms=" + DEFAULT_CONNECT_TIMEOUT_MS
                + ", status-interval-ms=" + DEFAULT_STATUS_INTERVAL_MS
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
