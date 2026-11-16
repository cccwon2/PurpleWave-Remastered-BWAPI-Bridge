package probe;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Verifies ArgvDump records exact argv elements (spaces / Unicode).
 * This is a process-argv check, not Remastered IPC.
 */
public class ArgvDumpTest {

    @Test
    public void dumpsSpaceAndKoreanArgsUnsplit() throws Exception {
        Path dir = Files.createTempDirectory("argv-dump");
        Path out = dir.resolve("argv.txt");
        String spacePath = dir.resolve("my jar.jar").toString();
        String korean = dir.resolve("작업폴더").toString();
        Files.createDirectories(dir.resolve("작업폴더"));

        int code = ArgvDump.dump(new String[]{
                out.toString(),
                spacePath,
                korean,
                "hello world"
        });
        assertEquals(0, code);

        List<String> lines = Files.readAllLines(out, StandardCharsets.UTF_8);
        assertEquals(3, lines.size());
        assertEquals(spacePath, lines.get(0));
        assertEquals(korean, lines.get(1));
        assertEquals("hello world", lines.get(2));
    }
}
