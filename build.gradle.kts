// build.gradle.kts — TherapyFlow Backend (Ktor)
// Kotlin 2.x + Ktor 3.x + Exposed + Koin + PostgreSQL

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

group = "io.therapyflow"
version = "0.1.0"

application {
    mainClass.set("io.therapyflow.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf(
        "-Dio.ktor.development=$isDevelopment",
        "-Djava.net.useSystemProxies=false"  // prevent SOCKS proxy interference with Google APIs
    )
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.request.validation)
    implementation(libs.ktor.server.default.headers)

    // Serialization
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // Database
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)

    // Dependency injection
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)

    // Auth
    implementation(libs.java.jwt)
    implementation(libs.bcrypt)

    // HTTP client (for Google APIs)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)

    // Logging
    implementation(libs.logback.classic)

    // PDF generation (migrated from existing PayrollApp)
    implementation(libs.itext7.core)

    // Excel generation (migrated from existing PayrollApp)
    implementation(libs.apache.poi.ooxml)

    // Testing
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit5)
    testImplementation(libs.mockk)
    testImplementation(libs.h2)  // in-memory DB for tests
}

tasks.test {
    useJUnitPlatform()
}
