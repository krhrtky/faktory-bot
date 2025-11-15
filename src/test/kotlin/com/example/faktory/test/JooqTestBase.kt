package com.example.faktory.test

import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager

@Testcontainers
abstract class JooqTestBase {
    protected lateinit var dsl: DSLContext

    companion object {
        @Container
        val mysql: MySQLContainer<*> =
            MySQLContainer("mysql:8.0")
                .withDatabaseName("faktory_test")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true)
    }

    @BeforeEach
    fun setupDatabase() {
        val connection =
            DriverManager.getConnection(
                mysql.jdbcUrl,
                mysql.username,
                mysql.password,
            )
        dsl = DSL.using(connection, SQLDialect.MYSQL)

        val schema =
            javaClass.getResourceAsStream("/schema.sql")
                ?.bufferedReader()
                ?.readText()
                ?: throw IllegalStateException("schema.sql not found")

        schema.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { dsl.execute(it) }
    }

    @AfterEach
    fun cleanupDatabase() {
        dsl.execute("SET FOREIGN_KEY_CHECKS = 0")
        dsl.execute("TRUNCATE TABLE posts")
        dsl.execute("TRUNCATE TABLE users")
        dsl.execute("SET FOREIGN_KEY_CHECKS = 1")
    }
}
