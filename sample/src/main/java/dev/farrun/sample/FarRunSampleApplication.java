package dev.farrun.sample;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

/** Small long-running application for manually exercising FarRun's remote Java strategy. */
public final class FarRunSampleApplication {
    private FarRunSampleApplication() {
    }

    public static void main(String[] args) throws Exception {
        long durationMillis = durationMillis(args);
        Instant deadline = Instant.now().plusMillis(durationMillis);
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception failure) {
            hostname = "<unavailable>";
        }

        System.out.println("[FarRun sample] Application started.");
        System.out.println("[FarRun sample] Java: " + System.getProperty("java.version"));
        System.out.println("[FarRun sample] Hostname: " + hostname);
        System.out.println("[FarRun sample] Arguments: " + Arrays.toString(args));

        int tick = 0;
        while (Instant.now().isBefore(deadline)) {
            tick++;
            System.out.println("[FarRun sample] stdout tick " + tick);
            if (tick % 3 == 0) {
                System.err.println("[FarRun sample] stderr diagnostic tick " + tick);
            }
            Thread.sleep(750);
        }
        System.out.println("[FarRun sample] Completed after " + Duration.ofMillis(durationMillis) + ".");
    }

    private static long durationMillis(String[] args) {
        for (int index = 0; index < args.length - 1; index++) {
            if ("--duration-ms".equals(args[index])) {
                try {
                    return Math.max(750, Long.parseLong(args[index + 1]));
                } catch (NumberFormatException ignored) {
                    return 30_000;
                }
            }
        }
        return 30_000;
    }
}
