package io.therapyflow.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("Database")

fun Application.configureDatabase() {
    val jdbcUrl  = System.getenv("DATABASE_URL")
        ?: "jdbc:postgresql://localhost:5432/therapyflow"
    val user     = System.getenv("DATABASE_USER")     ?: "therapyflow"
    val password = System.getenv("DATABASE_PASSWORD") ?: "therapyflow"

    // HikariCP connection pool
    val dataSource = HikariDataSource(HikariConfig().apply {
        this.jdbcUrl         = jdbcUrl
        this.username        = user
        this.password        = password
        driverClassName      = "org.postgresql.Driver"
        maximumPoolSize      = 10
        minimumIdle          = 2
        idleTimeout          = 600_000   // 10 min
        connectionTimeout    = 30_000    // 30 sec
        maxLifetime          = 1_800_000 // 30 min
        connectionTestQuery  = "SELECT 1"
    })

    // Flyway migrations — runs automatically at startup
    // Migration files: src/main/resources/db/migrations/
    log.info("Running Flyway migrations...")
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migrations")
        .baselineOnMigrate(true)
        .load()
        .migrate()
        .also { result ->
            log.info("Flyway: ${result.migrationsExecuted} migrations applied")
        }

    // Connect Exposed to the same pool
    Database.connect(dataSource)
    log.info("Database connected")
}
