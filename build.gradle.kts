import org.gradle.api.tasks.compile.JavaCompile

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "dev.farrun"
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("org.apache.sshd:sshd-core:2.19.0")
    implementation("org.apache.sshd:sshd-sftp:2.19.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")

    intellijPlatform {
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.plugins.gradle")
        bundledModule("intellij.gradle")

        val localIdeaPath = providers.gradleProperty("localIdeaPath")
        if (localIdeaPath.isPresent) {
            local(localIdeaPath.get())
        } else {
            intellijIdea(providers.gradleProperty("platformVersion"))
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

intellijPlatform {
    pluginConfiguration {
        id = "dev.farrun"
        name = "FarRun"
        version = providers.gradleProperty("pluginVersion")

        description = """
            <p>Run Java applications on remote Linux servers without leaving IntelliJ IDEA.</p>
            <p>FarRun builds locally, connects over SSH, prepares the required Java runtime,
            uploads the application classpath, and starts the remote JVM from the standard Run workflow.</p>
            <ul>
              <li>Java Application and supported Gradle application configurations</li>
              <li>SSH server management with IDE Password Safe integration</li>
              <li>Per-run status panel and native colored lifecycle logs</li>
            </ul>
            <h3>Quick start</h3>
            <ol>
              <li>Open <b>Settings | Tools | FarRun</b>, add an SSH server, and test the connection.</li>
              <li>Open <b>Run | Edit Configurations</b>, add a <b>FarRun</b> configuration, and select a Java Application or supported Gradle configuration.</li>
              <li>Select the saved server and run the configuration. FarRun reports each confirmed remote execution stage in the Run tool window.</li>
            </ol>
        """.trimIndent()

        changeNotes = """
            <ul>
              <li>Remote Java and Gradle execution over SSH</li>
              <li>Runtime provisioning, classpath upload, and process lifecycle visualization</li>
            </ul>
        """.trimIndent()

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }

        vendor {
            name = "FarRun"
        }
    }
}
