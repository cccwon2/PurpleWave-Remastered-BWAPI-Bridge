package probe;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JsonlLoggerTest {

    @Test
    public void writesRequiredFieldsWithNulls() throws Exception {
        Path dir = Files.createTempDirectory("probe-jsonl");
        Path file = dir.resolve("test.jsonl");
        ByteArrayOutputStream mirrorBytes = new ByteArrayOutputStream();
        PrintStream mirror = new PrintStream(mirrorBytes, true, "UTF-8");

        JsonlLogger logger = new JsonlLogger(file, mirror);
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("frame", 12);
        fields.put("playerId", 0);
        fields.put("race", "Protoss");
        fields.put("minerals", 50);
        fields.put("gas", 0);
        fields.put("supplyUsed", 8);
        fields.put("supplyTotal", 18);
        fields.put("selfUnitCount", 5);
        fields.put("errorCode", null);
        logger.log("INFO", ProbeState.MATCH_STARTED, fields, "hello \"world\"");
        logger.close();

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(1, lines.size());
        String line = lines.get(0);
        assertTrue(line.contains("\"event\":\"MATCH_STARTED\""));
        assertTrue(line.contains("\"frame\":12"));
        assertTrue(line.contains("\"errorCode\":null"));
        assertTrue(line.contains("\"message\":\"hello \\\"world\\\"\""));
        assertTrue(line.contains("\"elapsedMs\":"));
        assertTrue(line.contains("\"timestamp\":"));
        assertTrue(mirrorBytes.toString("UTF-8").contains("MATCH_STARTED"));
    }

    @Test
    public void escapeHandlesControlChars() {
        assertEquals("a\\\\b\\\"c\\n", JsonlLogger.escape("a\\b\"c\n"));
    }

    @Test
    public void valueToJsonNullAndNumber() {
        assertEquals("null", JsonlLogger.valueToJson(null));
        assertEquals("3", JsonlLogger.valueToJson(3));
        assertEquals("true", JsonlLogger.valueToJson(true));
        assertEquals("\"x\"", JsonlLogger.valueToJson("x"));
    }
}
