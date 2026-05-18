package com.yishape.lab.math.hpc.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link HpcNativePlatform} 与当前 JVM/OS 的 classifier、动态库扩展名约定的一致性检查。 */
class HpcNativePlatformTest {

    @Test
    void classifierLooksLikeOsMavenTriple() {
        String c = HpcNativePlatform.classifier();
        assertTrue(c.contains("-"), c);
        assertFalse(c.isBlank());
    }

    @Test
    void libraryNameMatchesOsFamily() {
        String n = HpcNativePlatform.bundledLibraryFileName();
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            assertTrue(n.endsWith(".dll"));
        } else if (os.contains("mac") || os.contains("darwin")) {
            assertTrue(n.endsWith(".dylib"));
        } else {
            assertTrue(n.endsWith(".so"));
        }
    }
}
