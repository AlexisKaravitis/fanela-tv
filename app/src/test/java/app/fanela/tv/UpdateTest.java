package app.fanela.tv;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UpdateTest {
    @Test public void versionOrder() {
        assertTrue(AppUpdate.compare("1.10.0", "1.9.2") > 0);
        assertTrue(AppUpdate.compare("1.0.1", "1.0.0") > 0);
        assertTrue(AppUpdate.compare("1.0", "1.0.0") == 0);
        assertTrue(AppUpdate.compare("1.0.0", "1.0.0-beta") == 0);
        assertTrue(AppUpdate.compare("0.9.9", "1.0.0") < 0);
    }
}
