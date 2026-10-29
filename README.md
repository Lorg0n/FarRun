# FarRun

FarRun is an IntelliJ IDEA plugin foundation for a future workflow that will build
Java applications locally and run them on remote Linux servers. Remote execution,
SSH, SFTP, debugging, and Run Configuration integration are intentionally not part
of this first version.

## Requirements

- JDK 25 or newer, with JDK 25 selected as the Gradle JVM.
- IntelliJ IDEA 2026.2 or newer with the Plugin DevKit enabled.

The project targets IntelliJ IDEA 2026.2.3 and uses the IntelliJ Platform Gradle
Plugin 2.x. The Gradle wrapper downloads the required Gradle version automatically.

## Open the project

1. In IntelliJ IDEA, select **File | Open** and choose the `FarRun` directory.
2. Trust the project if prompted.
3. Import it as a Gradle project and select JDK 25 as the Gradle JVM.

## Run a development IDE

From the project directory:

```bash
./gradlew runIde
```

This starts an isolated IntelliJ IDEA sandbox with FarRun installed. In that IDE,
open **Tools | FarRun: Hello**. A standard IntelliJ notification should show:

```text
FarRun plugin is running successfully!
```

## Build the plugin ZIP

```bash
./gradlew buildPlugin
```

The ZIP is written to `build/distributions/`.

## Install in a regular IntelliJ IDEA

1. Open **Settings | Plugins**.
2. Select the gear menu and choose **Install Plugin from Disk...**.
3. Choose the ZIP from `build/distributions/`.
4. Restart IntelliJ IDEA when prompted.

## Useful checks

```bash
./gradlew build
./gradlew test
./gradlew verifyPlugin
```

If the exact target IDE is already installed locally, its SDK can be reused instead
of downloading it:

```bash
./gradlew build -PlocalIdeaPath=/path/to/idea
```
