import com.vanniktech.maven.publish.SonatypeHost

plugins {
    kotlin("jvm") version "2.0.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
    id("com.vanniktech.maven.publish") version "0.28.0"
}

group = "com.gepheum.soia"
version = "1.0.0"

kotlin {
    compilerOptions {
        // Removed unsupported flag
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    implementation("com.squareup.okio:okio:3.6.0")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

tasks.test {
    useJUnitPlatform()
}

// From https://proandroiddev.com/publishing-kotlin-multiplatform-libraries-with-sonatype-central-b40f7cc6866e
mavenPublishing {
    // Define coordinates for the published artifact
    coordinates(
        groupId = "land.soia",
        artifactId = "soia-kotlin-client",
        version = "1.0.0"
    )

    // Configure POM metadata for the published artifact
    pom {
        name.set("Soia Kotlin Client")
        description.set("Soia Client for the Kotlin Language")
        inceptionYear.set("2024")
        url.set("https://github.com/gepheum/soia-kotlin-client")

        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
            }
        }

        // Specify developers information
        developers {
            developer {
                id.set("gepheum")
                name.set("Tyler Fibonacci")
                email.set("gepheum@gmail.com")
            }
        }

        // Specify SCM information
        scm {
            url.set("https://github.com/gepheum/soia-kotlin-client")
        }
    }

    // Configure publishing to Maven Central
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    // Enable GPG signing for all publications
    signAllPublications()
}
