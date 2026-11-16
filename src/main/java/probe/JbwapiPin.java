package probe;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Expected JBWAPI submodule commit — must match PurpleWave's 3rdparty/jbwapi pin.
 * Do not change this without an explicit PurpleWave pin change.
 */
public final class JbwapiPin {
    public static final String EXPECTED_SHA = "d6003b0b3a6a27944c979fd8dbc6ec8e3c2c753f";
    public static final int SUPPORTED_BWAPI_CLIENT_VERSION = 10003;

    private JbwapiPin() {
    }

    /**
     * @return resolved SHA, or null if the submodule checkout cannot be read
     */
    public static String readCheckedOutSha(Path repoRoot) throws IOException, InterruptedException {
        Path jbwapi = repoRoot.resolve("3rdparty").resolve("jbwapi");
        if (!Files.isDirectory(jbwapi)) {
            return null;
        }
        ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "HEAD");
        pb.directory(jbwapi.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String line;
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        try {
            line = reader.readLine();
        } finally {
            reader.close();
        }
        int code = process.waitFor();
        if (code != 0 || line == null || line.trim().isEmpty()) {
            return null;
        }
        return line.trim();
    }

    public static boolean matchesExpected(String sha) {
        return EXPECTED_SHA.equalsIgnoreCase(sha);
    }

    public static Path defaultRepoRoot() {
        return Paths.get("").toAbsolutePath().normalize();
    }
}
