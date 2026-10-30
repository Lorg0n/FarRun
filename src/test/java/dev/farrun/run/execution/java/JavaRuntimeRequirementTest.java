package dev.farrun.run.execution.java;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaRuntimeRequirementTest {
    @Test
    void parsesLegacyJava8SdkAndLanguageLevelStrings() {
        assertEquals(8, JavaRuntimeRequirement.parseMajorVersion("java version \"1.8.0_442\""));
        assertEquals(8, JavaRuntimeRequirement.parseMajorVersion("JDK_1_8"));
    }

    @Test
    void parsesModernJavaVersionStrings() {
        assertEquals(25, JavaRuntimeRequirement.parseMajorVersion("java version \"25.0.5\""));
        assertEquals(21, JavaRuntimeRequirement.parseMajorVersion("JDK_21"));
    }
}
