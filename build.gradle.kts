plugins {
    kotlin("jvm") version "2.0.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
    `maven-publish`
    signing
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
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

// Add sources and javadoc JARs
java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenKotlin") {
            groupId = "land.soia"
            artifactId = "soia-kotlin-client"
            version = "1.0.0"

            from(components["java"])

            pom {
                name.set("Soia Kotlin Client")
                description.set("Soia client for the Kotlin language")
                url.set("https://github.com/gepheum/soia-kotlin-client")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("gepheum")
                        name.set("Tyler Fibonacci")
                        email.set("gepheum@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/gepheum/soia-kotlin-client.git")
                    developerConnection.set("scm:git:ssh://github.com/gepheum/soia-kotlin-client.git")
                    url.set("https://github.com/gepheum/soia-kotlin-client")
                }
            }
        }
    }
}

signing {
    useInMemoryPgpKeys(
        project.findProperty("signingKey") as String? ?: "",
        project.findProperty("signingPassword") as String? ?: "",
    )
    // Sign all publications
    sign(publishing.publications["mavenKotlin"])
}

// Configure Nexus publishing
nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            username.set(project.findProperty("ossrhUsername") as String? ?: "")
            password.set(project.findProperty("ossrhPassword") as String? ?: "")
        }
    }
}
