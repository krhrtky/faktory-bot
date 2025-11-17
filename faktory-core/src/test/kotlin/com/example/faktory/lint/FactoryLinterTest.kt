package com.example.faktory.lint

import com.example.faktory.core.FactoryLintException
import com.example.faktory.dsl.factory
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import com.example.faktory.test.CommonUserTrait
import com.example.faktory.test.JooqTestBase
import com.example.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class FactoryLinterTest : JooqTestBase() {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    @Test
    fun `lint valid factory without traits`() {
        factory<UsersRecord> {
            attribute("name", "Test User")
            attribute("email") { "user@example.com" }
        }

        assertDoesNotThrow {
            FactoryLinter.lint(dsl)
        }
    }

    @Test
    fun `lint valid factory with traits`() {
        factory<UsersRecord> {
            attribute("name", "Test User")
            attribute("email") { "user@example.com" }

            trait(CommonUserTrait.Admin) {
                attribute("name", "Admin User")
                attribute("age", 30)
            }
        }

        assertDoesNotThrow {
            FactoryLinter.lint(dsl, traits = true)
        }
    }

    @Test
    fun `lint specific factory`() {
        factory<UsersRecord> {
            attribute("name", "Test User")
            attribute("email") { "user@example.com" }
        }

        assertDoesNotThrow {
            FactoryLinter.lint(dsl, UsersRecord::class)
        }
    }

    @Test
    fun `lint throws exception for invalid factory`() {
        factory<UsersRecord> {
            attribute("email") { "user@example.com" }
        }

        val exception =
            assertThrows<FactoryLintException> {
                FactoryLinter.lint(dsl)
            }

        assertThat(exception.recordClass).isEqualTo(UsersRecord::class)
        assertThat(exception.message).contains("UsersRecord")
        assertThat(exception.message).contains("Missing required attributes")
    }

    @Test
    fun `lint multiple factories`() {
        factory<UsersRecord>("default") {
            attribute("name", "User 1")
            attribute("email", "user1@example.com")
        }

        factory<UsersRecord>("admin") {
            attribute("name", "Admin")
            attribute("email", "admin@example.com")
        }

        assertDoesNotThrow {
            FactoryLinter.lint(dsl)
        }
    }
}
