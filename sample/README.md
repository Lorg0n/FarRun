# FarRun remote sample

This is an isolated Java application used to verify FarRun's Java Application strategy.
It is not part of the plugin production sources.

1. Open `sample/` as a separate IntelliJ project.
2. Create a regular **Java Application** run configuration.
3. Select `dev.farrun.sample.FarRunSampleApplication` as the main class.
4. Add a FarRun configuration in the sample project.
5. Select the Java Application and an SSH server configured under `Settings | Tools | FarRun`.
6. Run FarRun. Use `--duration-ms 30000` as program arguments and press Stop to test cancellation.

The sample prints Java version, hostname, arguments, periodic stdout, and periodic stderr without
printing environment variables or other secrets.
