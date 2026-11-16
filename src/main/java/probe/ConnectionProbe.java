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
import java.util.concurrent.atomic.AtomicBoolean;
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

    private final ProbeConfig config;
    private final JsonlLogger logger;
    private final BWClient bwClient;
    private final AtomicBoolean matchActive = new AtomicBoolean(false);
    private final AtomicBoolean timedOut = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicReference<ProbeState> state = new AtomicReference<ProbeState>(ProbeState.PROCESS_STARTED);
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

        if (config.getWorkingDirectory() != null) {
            Path workdir = config.getWorkingDirectory();
            if (!Files.isDirectory(workdir)) {
                System.err.println("Working directory does not exist or is not a directory: " + workdir);
                return EXIT_WORKDIR;
            }
            System.setProperty("user.dir", workdir.toAbsolutePath().toString());
        }

        Path logFile = config.getLogDirectory().resolve("connection-probe.jsonl");
        JsonlLogger logger;
        try {
            Files.createDirectories(config.getLogDirectory());
            logger = new JsonlLogger(logFile, System.out);
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

    private int execute() {
        clientThread = Thread.currentThread();
        printEnvironment();
        transition(ProbeState.PROCESS_STARTED, "INFO", "Connection probe process started");
        transition(ProbeState.WAITING_FOR_BRIDGE, "INFO",
                "Waiting for BWAPI shared-memory bridge (Local\\\\bwapi_shared_memory_game_list). "
                        + "Public JBWAPI listener API has no separate CONNECTED callback; "
                        + "next observable event is MATCH_STARTED via onStart().");

        startConnectWatchdog();

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
                transition(ProbeState.FAILED, "ERROR", "Connect timeout or interrupt: " + t.getClass().getSimpleName()
                        + ": " + String.valueOf(t.getMessage()));
                return EXIT_TIMEOUT;
            }
            Map<String, Object> fields = new LinkedHashMap<String, Object>();
            fields.put("errorCode", t.getClass().getName());
            logger.log("ERROR", ProbeState.FAILED, fields, "Unhandled failure: " + t.getMessage());
            state.set(ProbeState.FAILED);
            return EXIT_FAILED;
        }
    }

    private void startConnectWatchdog() {
        final long timeoutMs = config.getConnectTimeoutMs();
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
                fields.put("errorCode", "CONNECT_TIMEOUT");
                logger.log("ERROR", ProbeState.FAILED, fields,
                        "Connect timeout after " + timeoutMs + "ms while still waiting for match start. "
                                + "JBWAPI BWClient has no public stop(); exiting probe process only.");
                state.set(ProbeState.FAILED);
                // Interrupt the blocking reconnect/startGame loop, then hard-exit if needed.
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
        }, "probe-connect-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private void printEnvironment() {
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("errorCode", null);
        String jvmName = System.getProperty("java.vm.name");
        String javaVersion = System.getProperty("java.version");
        String javaHome = System.getProperty("java.home");
        String osName = System.getProperty("os.name");
        String osArch = System.getProperty("os.arch");
        String dataModel = System.getProperty("sun.arch.data.model");
        String message = "env javaVersion=" + javaVersion
                + " javaHome=" + javaHome
                + " vm=" + jvmName
                + " os=" + osName + "/" + osArch
                + " dataModel=" + dataModel
                + " jbwapiCommit=d6003b0b3a6a27944c979fd8dbc6ec8e3c2c753f"
                + " jbwapiSupportedBwapiClientVersion=10003"
                + " cwd=" + Paths.get("").toAbsolutePath()
                + " runtimeMx=" + ManagementFactory.getRuntimeMXBean().getName()
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
            fields.put("frame", null);
            fields.put("playerId", null);
            fields.put("race", null);
            fields.put("minerals", null);
            fields.put("gas", null);
            fields.put("supplyUsed", null);
            fields.put("supplyTotal", null);
            fields.put("selfUnitCount", null);
            return fields;
        }
        try {
            fields.put("frame", game.getFrameCount());
        } catch (Throwable t) {
            fields.put("frame", null);
        }
        Player self = null;
        try {
            self = game.self();
        } catch (Throwable t) {
            self = null;
        }
        if (self == null) {
            fields.put("playerId", null);
            fields.put("race", null);
            fields.put("minerals", null);
            fields.put("gas", null);
            fields.put("supplyUsed", null);
            fields.put("supplyTotal", null);
            fields.put("selfUnitCount", null);
            return fields;
        }
        try {
            fields.put("playerId", self.getID());
        } catch (Throwable t) {
            fields.put("playerId", null);
        }
        try {
            fields.put("race", self.getRace() == null ? null : self.getRace().toString());
        } catch (Throwable t) {
            fields.put("race", null);
        }
        try {
            fields.put("minerals", self.minerals());
        } catch (Throwable t) {
            fields.put("minerals", null);
        }
        try {
            fields.put("gas", self.gas());
        } catch (Throwable t) {
            fields.put("gas", null);
        }
        try {
            // BWAPI internal supply units; in-game display supply is typically internal/2.
            fields.put("supplyUsed", self.supplyUsed());
        } catch (Throwable t) {
            fields.put("supplyUsed", null);
        }
        try {
            fields.put("supplyTotal", self.supplyTotal());
        } catch (Throwable t) {
            fields.put("supplyTotal", null);
        }
        try {
            fields.put("selfUnitCount", self.getUnits() == null ? null : self.getUnits().size());
        } catch (Throwable t) {
            fields.put("selfUnitCount", null);
        }
        return fields;
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
