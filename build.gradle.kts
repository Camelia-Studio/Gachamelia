import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("com.gradleup.shadow") version "9.4.3"
}

group = "org.camelia.studio.gachamelia"
version = "0.0.1"

repositories {
    mavenCentral()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

tasks.withType<ShadowJar>().configureEach {
    manifest {
        attributes["Main-Class"] = "org.camelia.studio.gachamelia.Gachamelia"
    }

    archiveFileName.set("gachamelia.jar")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    implementation("org.hibernate.orm:hibernate-core:7.4.3.Final")
    implementation("org.hibernate.orm:hibernate-hikaricp:7.4.3.Final")
    implementation("org.postgresql:postgresql:42.7.12")
    implementation("io.github.cdimascio:dotenv-kotlin:6.5.1")
    implementation("net.dv8tion:JDA:6.4.2")
    implementation("ch.qos.logback:logback-classic:1.5.37")
    implementation("jakarta.annotation:jakarta.annotation-api:3.0.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.0")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.1")
    testImplementation("org.assertj:assertj-core:4.0.0-M1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}


java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
