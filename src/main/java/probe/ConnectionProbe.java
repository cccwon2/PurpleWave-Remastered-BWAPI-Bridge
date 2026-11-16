package probe;

import bwapi.BWClient;
import bwapi.BWClientConfiguration;
import bwapi.DefaultBWListener;
import bwapi.Game;
import bwapi.Player;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Read-only connection diagnostic using the same JBWAPI commit PurpleWave pins.
 * Does not issue unit/building commands or change game speed.
 */
public final class ConnectionProbe extends DefaultBWListener {
    public static final int EXIT_OK = 0;
    public static final int EXIT_USAGE = 2;
    public static final int EXIT_CONFIG = 3;
    public static final int EXIT_LOG_DIR = 4;
    public static final int EXIT_WORKDIR = 5;
    public static final int EXIT_TIMEOUT = 6;
    public static final int EXIT_FAILED = 7;
    public static final int EXIT_PIN_MISMATCH = 8;

    public static final String ERROR_MATCH_START_WAIT_EXCEEDED = "MATCH_START_WAIT_EXCEEDED";
    public static final String ERROR_WINDOWS_API_UNAVAILABLE = "WINDOWS_API_UNAVAILABLE";
    public static final String ERROR_NO_BRIDGE_OR_WAITING = "NO_BRIDGE_OR_WAITING";
    public static final String ERROR_OVERALL_RUN_TIMEOUT = "OVERALL_RUN_TIMEOUT";
    public static final String ERROR_JBWAPI_PIN_MISMATCH = "JBWAPI_PIN_MISMATCH";

    private static final long FIELD_ERROR_LOG_INTERVAL_MS = 5_000L;

    private final ProbeConfig config;
    private final JsonlLogger logger;
    private final BWClient bwClient;
    private final AtomicBoolean matchActive = new AtomicBoolean(false);
    private final AtomicBoolean timedOut = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicReference<ProbeState> state = new AtomicReference<ProbeState>(ProbeState.PROCESS_STARTED);
    private final ConcurrentHashMap<String, AtomicLong> lastFieldErrorLogMs = new ConcurrentHashMap<String, AtomicLong>();
    private volatile long lastStatusEmitMs = 0L;
    private volatile Thread clientThread;

    ConnectionProbe(ProbeConfig config, JsonlLogger logger) {
        this.config = config;
        this.logger = logger;
        this.bwClient = new BWClient(this);
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        return run(args, JbwapiPin.defaultRepoRoot());
    }

    static int run(String[] args, Path repoRootForPinCheck) {
        ProbeConfig config;
        try {
            config = ProbeConfig.parse(args);
        } catch (ProbeConfig.HelpRequestedException help) {
            System.out.println(help.getMessage());
            return EXIT_USAGE;
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            System.err.println(ProbeConfig.usage());
            return EXIT_CONFIG;
        }

        int pinCode = verifyJbwapiPin(repoRootForPinCheck);
        if (pinCode != EXIT_OK) {
            return pinCode;
        }

        if (config.getWorkingDirectory() != null) {
            Path workdir = config.getWorkingDirectory();
            if (!Files.isDirectory(workdir)) {
                System.err.println("Working directory does not exist or is not a directory: " + workdir);
                return EXIT_WORKDIR;
            }
            System.setProperty("user.dir", workdir.toAbsolutePath().toString());
        }

        Path logFile = config.resolveJsonlPath();
        JsonlLogger logger;
        try {
            Files.createDirectories(config.getLogDirectory());
            logger = new JsonlLogger(logFile, System.out, config.getRunId());
        } catch (IOException ex) {
            System.err.println("Failed to create log directory/file: " + ex.getMessage());
            return EXIT_LOG_DIR;
        }

        ConnectionProbe probe = new ConnectionProbe(config, logger);
        try {
            return probe.execute();
        } finally {
            try {
                logger.close();
            } catch (IOException ignored) {
                // best-effort close
            }
        }
    }

    static int verifyJbwapiPin(Path repoRoot) {
        if (repoRoot == null) {
            return EXIT_OK;
        }
        // Allow tests to skip when submodule path is absent.
        Path jbwapi = repoRoot.resolve("3rdparty").resolve("jbwapi");
        if (!Files.isDirectory(jbwapi)) {
            System.err.println("WARN: 3rdparty/jbwapi missing under " + repoRoot + "; skipping pin check");
            return EXIT_OK;
        }
        try {
            String sha = JbwapiPin.readCheckedOutSha(repoRoot);
            if (sha == null) {
                System.err.println("WARN: could not read JBWAPI submodule SHA; continuing");
                return EXIT_OK;
            }
            if (!JbwapiPin.matchesExpected(sha)) {
                System.err.println("JBWAPI pin mismatch: expected " + JbwapiPin.EXPECTED_SHA + " but found " + sha);
                return EXIT_PIN_MISMATCH;
            }
        } catch (Exception ex) {
            System.err.println("WARN: JBWAPI pin check failed: " + ex.getMessage());
        }
        return EXIT_OK;
    }

    private int execute() {
        clientThread = Thread.currentThread();
        printEnvironment();
        transition(ProbeState.PROCESS_STARTED, "INFO",
                "Connection probe process started runId=" + config.getRunId()
                        + " jsonl=" + config.resolveJsonlPath());
        transition(ProbeState.WAITING_FOR_BRIDGE, "INFO",
                "Match-start wait began (NOT a verified IPC-connect wait). "
                        + "Public JBWAPI listener API has no CONNECTED callback. "
                        + "Looking for Local\\\\bwapi_shared_memory_game_list then onStart()/MATCH_STARTED. "
                        + "matchStartWaitMs=" + config.getMatchStartWaitMs()
                        + " overallRunTimeoutMs=" + config.getOverallRunTimeoutMs());

        startMatchStartWatchdog();
        startOverallRunWatchdog();

        BWClientConfiguration configuration = new BWClientConfiguration()
                .withAutoContinue(false)
                .withAsync(false)
                .withDebugConnection(true);

        try {
            bwClient.startGame(configuration);
            if (timedOut.get()) {
                return EXIT_TIMEOUT;
            }
            if (state.get() == ProbeState.FAILED) {
                return EXIT_FAILED;
            }
            return EXIT_OK;
        } catch (Throwable t) {
            if (timedOut.get() || isInterrupt(t)) {
                String code = matchActive.get() ? ERROR_OVERALL_RUN_TIMEOUT : ERROR_MATCH_START_WAIT_EXCEEDED;
                Map<String, Object> fields = new LinkedHashMap<String, Object>();
                fields.put("errorCode", code);
                logger.log("ERROR", ProbeState.FAILED, fields,
                        "Timeout/interrupt: " + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
                state.set(ProbeState.FAILED);
                return EXIT_TIMEOUT;
            }
            String errorCode = classifyFailure(t);
            Map<String, Object> fields = new LinkedHashMap<String, Object>();
            fields.put("errorCode", errorCode);
            logger.log("ERROR", ProbeState.FAILED, fields,
                    "Unhandled failure (" + errorCode + "): " + t.getClass().getName() + ": " + t.getMessage());
            state.set(ProbeState.FAILED);
            return EXIT_FAILED;
        }
    }

    static String classifyFailure(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            String name = cur.getClass().getName();
            String msg = String.valueOf(cur.getMessage()).toLowerCase();
            if (cur instanceof UnsatisfiedLinkError
                    || name.contains("UnsatisfiedLinkError")
                    || msg.contains("kernel32")
                    || msg.contains("libkernel32")) {
                return ERROR_WINDOWS_API_UNAVAILABLE;
            }
            cur = cur.getCause();
        }
        return ERROR_NO_BRIDGE_OR_WAITING;
    }

    private void startMatchStartWatchdog() {
        final long timeoutMs = config.getMatchStartWaitMs();
        Thread watchdog = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(timeoutMs);
                } catch (InterruptedException e) {
                    return;
                }
                if (matchActive.get() || shuttingDown.get()) {
                    return;
                }
                timedOut.set(true);
                Map<String, Object> fields = new LinkedHashMap<String, Object>();
                fields.put("errorCode", ERROR_MATCH_START_WAIT_EXCEEDED);
                logger.log("ERROR", ProbeState.FAILED, fields,
                        "Match-start wait exceeded after " + timeoutMs + "ms (still no onStart). "
                                + "This is not proof of IPC connect failure vs absent bridge — only that MATCH_STARTED "
                                + "was not observed. JBWAPI BWClient has no public stop(); exiting probe only.");
                state.set(ProbeState.FAILED);
                Thread target = clientThread;
                if (target != null) {
                    target.interrupt();
                }
                try {
                    Thread.sleep(1500L);
                } catch (InterruptedException ignored) {
                    // ignore
                }
                if (!shuttingDown.get() && !matchActive.get()) {
                    System.exit(EXIT_TIMEOUT);
                }
            }
        }, "probe-match-start-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private void startOverallRunWatchdog() {
        final long timeoutMs = config.getOverallRunTimeoutMs();
        if (timeoutMs <= 0) {
            return;
        }
        Thread watchdog = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(timeoutMs);
                } catch (InterruptedException e) {
                    return;
                }
                if (shuttingDown.get()) {
                    return;
                }
                timedOut.set(true);
                Map<String, Object> fields = new LinkedHashMap<String, Object>();
                fields.put("errorCode", ERROR_OVERALL_RUN_TIMEOUT);
                logger.log("ERROR", ProbeState.FAILED, fields,
                        "Overall run timeout after " + timeoutMs + "ms; stopping probe process only.");
                state.set(ProbeState.FAILED);
                Thread target = clientThread;
                if (target != null) {
                    target.interrupt();
                }
                try {
                    Thread.sleep(1500L);
                } catch (InterruptedException ignored) {
                    // ignore
                }
                if (!shuttingDown.get()) {
                    System.exit(EXIT_TIMEOUT);
                }
            }
        }, "probe-overall-run-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private void printEnvironment() {
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("errorCode", null);
        String message = "env javaVersion=" + System.getProperty("java.version")
                + " javaHome=" + System.getProperty("java.home")
                + " vm=" + System.getProperty("java.vm.name")
                + " os=" + System.getProperty("os.name") + "/" + System.getProperty("os.arch")
                + " dataModel=" + System.getProperty("sun.arch.data.model")
                + " jbwapiCommit=" + JbwapiPin.EXPECTED_SHA
                + " jbwapiSupportedBwapiClientVersion=" + JbwapiPin.SUPPORTED_BWAPI_CLIENT_VERSION
                + " jnaAlignedToPurpleWave=5.1.0"
                + " cwd=" + Paths.get("").toAbsolutePath()
                + " runtimeMx=" + ManagementFactory.getRuntimeMXBean().getName()
                + " runId=" + config.getRunId()
                + " note=supplyUsed/supplyTotal are BWAPI internal units (display = internal/2)";
        logger.log("INFO", ProbeState.PROCESS_STARTED, fields, message);
    }

    @Override
    public void onStart() {
        matchActive.set(true);
        Game game = bwClient.getGame();
        Map<String, Object> snapshot = snapshot(game);
        transition(ProbeState.MATCH_STARTED, "INFO", "Match started (onStart)");
        logger.log("INFO", ProbeState.MATCH_STARTED, snapshot, "Initial match snapshot");
        lastStatusEmitMs = System.currentTimeMillis();
    }

    @Override
    public void onFrame() {
        if (!matchActive.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastStatusEmitMs < config.getStatusIntervalMs()) {
            return;
        }
        lastStatusEmitMs = now;
        Game game = bwClient.getGame();
        Map<String, Object> snapshot = snapshot(game);
        logger.log("INFO", ProbeState.MATCH_STARTED, snapshot, "Periodic status");
    }

    @Override
    public void onEnd(boolean isWinner) {
        Game game = bwClient.getGame();
        Map<String, Object> snapshot = snapshot(game);
        snapshot.put("errorCode", null);
        logger.log("INFO", ProbeState.MATCH_ENDED, snapshot, "Match ended isWinner=" + isWinner);
        state.set(ProbeState.MATCH_ENDED);
        matchActive.set(false);
        shuttingDown.set(true);
    }

    private Map<String, Object> snapshot(Game game) {
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        if (game == null) {
            putNull(fields, "frame");
            putNull(fields, "playerId");
            putNull(fields, "race");
            putNull(fields, "minerals");
            putNull(fields, "gas");
            putNull(fields, "supplyUsed");
            putNull(fields, "supplyTotal");
            putNull(fields, "selfUnitCount");
            return fields;
        }
        readField(fields, "frame", new FieldReader() {
            @Override
            public Object read() {
                return game.getFrameCount();
            }
        });
        Player self = null;
        try {
            self = game.self();
        } catch (Throwable t) {
            logFieldFailure("self", t);
            self = null;
        }
        if (self == null) {
            putNull(fields, "playerId");
            putNull(fields, "race");
            putNull(fields, "minerals");
            putNull(fields, "gas");
            putNull(fields, "supplyUsed");
            putNull(fields, "supplyTotal");
            putNull(fields, "selfUnitCount");
            return fields;
        }
        final Player player = self;
        readField(fields, "playerId", new FieldReader() {
            @Override
            public Object read() {
                return player.getID();
            }
        });
        readField(fields, "race", new FieldReader() {
            @Override
            public Object read() {
                return player.getRace() == null ? null : player.getRace().toString();
            }
        });
        readField(fields, "minerals", new FieldReader() {
            @Override
            public Object read() {
                return player.minerals();
            }
        });
        readField(fields, "gas", new FieldReader() {
            @Override
            public Object read() {
                return player.gas();
            }
        });
        readField(fields, "supplyUsed", new FieldReader() {
            @Override
            public Object read() {
                return player.supplyUsed();
            }
        });
        readField(fields, "supplyTotal", new FieldReader() {
            @Override
            public Object read() {
                return player.supplyTotal();
            }
        });
        readField(fields, "selfUnitCount", new FieldReader() {
            @Override
            public Object read() {
                return player.getUnits() == null ? null : Integer.valueOf(player.getUnits().size());
            }
        });
        return fields;
    }

    private interface FieldReader {
        Object read() throws Exception;
    }

    private void readField(Map<String, Object> fields, String name, FieldReader reader) {
        try {
            fields.put(name, reader.read());
        } catch (Throwable t) {
            fields.put(name, null);
            logFieldFailure(name, t);
        }
    }

    private void putNull(Map<String, Object> fields, String name) {
        fields.put(name, null);
    }

    private void logFieldFailure(String fieldName, Throwable t) {
        long now = System.currentTimeMillis();
        AtomicLong last = lastFieldErrorLogMs.get(fieldName);
        if (last == null) {
            last = new AtomicLong(0L);
            AtomicLong prev = lastFieldErrorLogMs.putIfAbsent(fieldName, last);
            if (prev != null) {
                last = prev;
            }
        }
        long previous = last.get();
        if (now - previous < FIELD_ERROR_LOG_INTERVAL_MS) {
            return;
        }
        if (!last.compareAndSet(previous, now)) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("errorCode", "FIELD_READ_FAILED:" + fieldName);
        logger.log("WARN", state.get(), fields,
                "Field read failed for '" + fieldName + "': " + t.getClass().getSimpleName()
                        + ": " + String.valueOf(t.getMessage()));
    }

    private void transition(ProbeState next, String level, String message) {
        state.set(next);
        logger.logSimple(level, next, message);
    }

    private static boolean isInterrupt(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof InterruptedException) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}
