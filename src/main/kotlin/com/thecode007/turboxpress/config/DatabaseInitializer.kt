package com.thecode007.turboxpress.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.EnvironmentAware
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.net.URI
import java.sql.DriverManager

/**
 * Ensures the target PostgreSQL database exists BEFORE Spring attempts to
 * initialize the HikariCP connection pool.
 *
 * BeanFactoryPostProcessor is guaranteed to run before any regular bean
 * (including DataSource / HikariCP) is instantiated, which is exactly
 * what we need so that the DB is present when the pool first connects.
 */
@Component
class DatabaseInitializer : BeanFactoryPostProcessor, EnvironmentAware {

    private val log = LoggerFactory.getLogger(DatabaseInitializer::class.java)

    private lateinit var environment: Environment

    override fun setEnvironment(environment: Environment) {
        this.environment = environment
    }

    override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
        val datasourceUrl = environment.getProperty("spring.datasource.url") ?: return
        val username      = environment.getProperty("spring.datasource.username") ?: return
        val password      = environment.getProperty("spring.datasource.password") ?: return

        try {
            // Parse host, port and target DB name from the JDBC URL
            // Expected format: jdbc:postgresql://host:port/dbname
            val uri      = URI(datasourceUrl.removePrefix("jdbc:"))
            val host     = uri.host
            val port     = if (uri.port == -1) 5432 else uri.port
            val targetDb = uri.path.removePrefix("/").substringBefore("?")

            // Connect to the 'postgres' maintenance DB — always exists
            val maintenanceUrl = "jdbc:postgresql://$host:$port/postgres"

            log.info("=== DatabaseInitializer: checking if '$targetDb' exists on $host:$port ===")

            DriverManager.getConnection(maintenanceUrl, username, password).use { conn ->
                conn.autoCommit = true          // required for CREATE DATABASE
                conn.createStatement().use { stmt ->
                    stmt.executeQuery(
                        "SELECT 1 FROM pg_database WHERE datname = '$targetDb'"
                    ).use { rs ->
                        if (rs.next()) {
                            log.info("Database '$targetDb' already exists — skipping creation.")
                        } else {
                            log.warn("Database '$targetDb' not found — creating it now …")
                            stmt.execute("CREATE DATABASE \"$targetDb\"")
                            log.info("Database '$targetDb' created successfully.")
                        }
                    }
                }
            }

        } catch (ex: Exception) {
            log.error(
                "DatabaseInitializer failed — make sure PostgreSQL is running " +
                "and credentials in application.properties are correct.",
                ex
            )
            throw ex    // Abort startup; the app cannot run without the database.
        }
    }
}
