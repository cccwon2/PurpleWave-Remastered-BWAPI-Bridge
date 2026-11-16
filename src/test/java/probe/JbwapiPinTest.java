package probe;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class JbwapiPinTest {

    @Test
    public void expectedShaIsPurpleWavePin() {
        assertEquals("d6003b0b3a6a27944c979fd8dbc6ec8e3c2c753f", JbwapiPin.EXPECTED_SHA);
        assertEquals(10003, JbwapiPin.SUPPORTED_BWAPI_CLIENT_VERSION);
    }

    @Test
    public void matchesExpectedIsCaseInsensitive() {
        assertTrue(JbwapiPin.matchesExpected(JbwapiPin.EXPECTED_SHA));
        assertTrue(JbwapiPin.matchesExpected(JbwapiPin.EXPECTED_SHA.toUpperCase()));
        assertFalse(JbwapiPin.matchesExpected("0000000000000000000000000000000000000000"));
    }

    @Test
    public void checkoutMatchesExpectedInThisRepo() throws Exception {
        Path root = Paths.get("").toAbsolutePath().normalize();
        assumeTrue("submodule missing", Files.isDirectory(root.resolve("3rdparty/jbwapi")));
        String sha = JbwapiPin.readCheckedOutSha(root);
        assertTrue("readable sha", sha != null && !sha.isEmpty());
        assertTrue("pin mismatch: " + sha, JbwapiPin.matchesExpected(sha));
    }

    @Test
    public void verifyDetectsMismatchAgainstBogusExpected() throws Exception {
        // Direct mismatch detection without mutating the constant: compare read SHA to a fake.
        Path root = Paths.get("").toAbsolutePath().normalize();
        assumeTrue(Files.isDirectory(root.resolve("3rdparty/jbwapi")));
        String sha = JbwapiPin.readCheckedOutSha(root);
        assertFalse(JbwapiPin.matchesExpected("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
        assertTrue(JbwapiPin.matchesExpected(sha));
    }
}
