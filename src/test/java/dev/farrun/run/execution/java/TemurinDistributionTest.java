package dev.farrun.run.execution.java;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemurinDistributionTest {
    @Test
    void mapsSupportedLinuxArchitectures() {
        assertEquals(new TemurinDistribution("x64", "glibc"),
                TemurinDistribution.from("x86_64", "ldd (GNU libc) 2.39"));
        assertEquals(new TemurinDistribution("aarch64", "glibc"),
                TemurinDistribution.from("arm64", "ldd (GNU libc) 2.39"));
    }

    @Test
    void rejectsUnsupportedArchitectureAndMusl() {
        assertThrows(IllegalArgumentException.class,
                () -> TemurinDistribution.from("riscv64", "glibc"));
        assertThrows(IllegalArgumentException.class,
                () -> TemurinDistribution.from("x86_64", "musl libc"));
    }
}
