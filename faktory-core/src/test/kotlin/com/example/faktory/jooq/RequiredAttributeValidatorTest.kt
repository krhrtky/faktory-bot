package com.example.faktory.jooq

import com.example.faktory.core.MissingRequiredAttributesException
import com.example.faktory.dsl.factory
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import com.example.faktory.test.JooqTestBase
import com.example.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RequiredAttributeValidatorTest : JooqTestBase() {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    @Test
    fun `build succeeds when all required attributes are provided`() {
        factory<UsersRecord> {
            attribute("name", "John Doe")
            attribute("email", "john@example.com")
        }

        val user = dsl.factory<UsersRecord>().build()

        assertThat(user.name).isEqualTo("John Doe")
        assertThat(user.email).isEqualTo("john@example.com")
    }

    @Test
    fun `build fails when required attribute is missing`() {
        factory<UsersRecord> {
            attribute("name", "John Doe")
            // email is missing (NOT NULL field)
        }

        assertThatThrownBy {
            dsl.factory<UsersRecord>().build()
        }.isInstanceOf(MissingRequiredAttributesException::class.java)
            .hasMessageContaining("email")
    }

    @Test
    fun `build fails when multiple required attributes are missing`() {
        factory<UsersRecord> {
            // both name and email are missing (NOT NULL fields)
        }

        assertThatThrownBy {
            dsl.factory<UsersRecord>().build()
        }.isInstanceOf(MissingRequiredAttributesException::class.java)
            .hasMessageContaining("name")
            .hasMessageContaining("email")
    }

    @Test
    fun `create succeeds when all required attributes are provided`() {
        factory<UsersRecord> {
            attribute("name", "John Doe")
            attribute("email", "john@example.com")
        }

        val user = dsl.factory<UsersRecord>().create()

        assertThat(user.id).isNotNull()
        assertThat(user.name).isEqualTo("John Doe")
        assertThat(user.email).isEqualTo("john@example.com")
    }

    @Test
    fun `create fails when required attribute is missing`() {
        factory<UsersRecord> {
            attribute("name", "John Doe")
            // email is missing (NOT NULL field)
        }

        assertThatThrownBy {
            dsl.factory<UsersRecord>().create()
        }.isInstanceOf(MissingRequiredAttributesException::class.java)
            .hasMessageContaining("email")
    }

    @Test
    fun `optional attributes do not cause validation errors`() {
        factory<UsersRecord> {
            attribute("name", "John Doe")
            attribute("email", "john@example.com")
            // age is optional (nullable field) - not providing it is OK
        }

        val user = dsl.factory<UsersRecord>().build()

        assertThat(user.name).isEqualTo("John Doe")
        assertThat(user.email).isEqualTo("john@example.com")
        assertThat(user.age).isNull()
    }
}
