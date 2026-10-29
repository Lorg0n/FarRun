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
    intellijPlatform {
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

intellijPlatform {
    pluginConfiguration {
        id = "dev.farrun"
        name = "FarRun"
        version = providers.gradleProperty("pluginVersion")

        description = """
            FarRun provides the foundation for running Java applications on remote Linux servers.
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
