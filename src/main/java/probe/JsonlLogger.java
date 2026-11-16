package probe;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Appends one JSON object per line. Unreadable measured fields stay null.
 * Every line includes {@code runId} so supervisors can ignore stale logs.
 */
public final class JsonlLogger implements Closeable {
    private final BufferedWriter writer;
    private final PrintStream mirror;
    private final long startEpochMs;
    private final String runId;
    private final Object lock = new Object();

    public JsonlLogger(Path logFile, PrintStream mirror, String runId) throws IOException {
        Path parent = logFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        this.writer = new BufferedWriter(new OutputStreamWriter(
                Files.newOutputStream(logFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND),
                StandardCharsets.UTF_8));
        this.mirror = mirror;
        this.startEpochMs = System.currentTimeMillis();
        this.runId = runId == null ? "unknown" : runId;
    }

    public long getStartEpochMs() {
        return startEpochMs;
    }

    public String getRunId() {
        return runId;
    }

    public void log(String level, ProbeState event, Map<String, Object> fields, String message) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("timestamp", Instant.now().toString());
        row.put("level", level == null ? "INFO" : level);
        row.put("event", event == null ? null : event.name());
        row.put("runId", runId);
        row.put("elapsedMs", System.currentTimeMillis() - startEpochMs);
        putNullable(row, "frame", fields);
        putNullable(row, "playerId", fields);
        putNullable(row, "race", fields);
        putNullable(row, "minerals", fields);
        putNullable(row, "gas", fields);
        putNullable(row, "supplyUsed", fields);
        putNullable(row, "supplyTotal", fields);
        putNullable(row, "selfUnitCount", fields);
        putNullable(row, "errorCode", fields);
        row.put("message", message);
        writeLine(toJson(row));
    }

    public void logSimple(String level, ProbeState event, String message) {
        log(level, event, null, message);
    }

    private static void putNullable(Map<String, Object> row, String key, Map<String, Object> fields) {
        if (fields != null && fields.containsKey(key)) {
            row.put(key, fields.get(key));
        } else {
            row.put(key, null);
        }
    }

    private void writeLine(String json) {
        synchronized (lock) {
            try {
                writer.write(json);
                writer.newLine();
                writer.flush();
            } catch (IOException ex) {
                if (mirror != null) {
                    mirror.println("JSONL write failed: " + ex.getMessage());
                }
            }
            if (mirror != null) {
                mirror.println(json);
            }
        }
    }

    static String toJson(Map<String, Object> row) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(entry.getKey())).append('"').append(':');
            sb.append(valueToJson(entry.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    static String valueToJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return '"' + escape(String.valueOf(value)) + '"';
    }

    static String escape(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    @Override
    public void close() throws IOException {
        synchronized (lock) {
            writer.close();
        }
    }
}
