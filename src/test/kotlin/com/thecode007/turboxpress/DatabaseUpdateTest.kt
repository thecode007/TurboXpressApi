package com.thecode007.turboxpress

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest
class DatabaseUpdateTest {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun updateSchema() {
        try {
            jdbcTemplate.execute("ALTER TABLE delivery_guys ADD COLUMN billing_cycle VARCHAR(20) NOT NULL DEFAULT 'MONTHLY'")
            println("Added billing_cycle column.")
        } catch (e: Exception) {
            println("billing_cycle already exists or error: \${e.message}")
        }
        
        try {
            jdbcTemplate.execute("ALTER TABLE delivery_guys ADD COLUMN next_billing_date DATE NOT NULL DEFAULT '2026-05-08'")
            println("Added next_billing_date column.")
        } catch (e: Exception) {
            println("next_billing_date already exists or error: \${e.message}")
        }
    }
}
