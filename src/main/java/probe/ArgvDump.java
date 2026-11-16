package probe;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Test helper main: writes each received argv element as one UTF-8 line to a file.
 * Usage: java -cp ... probe.ArgvDump &lt;output-file&gt; [args...]
 */
public final class ArgvDump {
    public static void main(String[] args) throws Exception {
        int code = dump(args);
        System.exit(code);
    }

    /** @return process exit code (0 ok, 2 usage) */
    public static int dump(String[] args) throws Exception {
        if (args == null || args.length < 1) {
            System.err.println("Usage: ArgvDump <output-file> [args...]");
            return 2;
        }
        Path out = Paths.get(args[0]);
        Path parent = out.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(Files.newOutputStream(out), StandardCharsets.UTF_8));
        try {
            for (int i = 1; i < args.length; i++) {
                writer.write(args[i]);
                writer.newLine();
            }
        } finally {
            writer.close();
        }
        System.out.println("argv-count=" + (args.length - 1));
        return 0;
    }
}
