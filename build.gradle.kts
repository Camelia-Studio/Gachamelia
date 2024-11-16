import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.camelia.studio.gachamelia"
version = "0.0.1"

repositories {
    mavenCentral()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<ShadowJar> {
    manifest {
        attributes["Main-Class"] = "org.camelia.studio.gachamelia.Gachamelia"
    }

    archiveFileName.set("gachamelia.jar")
}

dependencies {
    implementation("org.hibernate:hibernate-core:6.6.2.Final")
    implementation("org.hibernate:hibernate-hikaricp:6.6.2.Final")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.2")
    implementation("net.dv8tion:JDA:5.2.1")
    implementation("ch.qos.logback:logback-classic:1.5.12")
}


java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}