package com.example.faktory.builder

import com.example.faktory.core.DefaultFactoryDefinition
import com.example.faktory.core.StaticAttribute
import com.example.faktory.test.JooqTestBase
import com.example.faktory.test.jooq.tables.Users
import com.example.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

class DefaultFactoryBuilderTest : JooqTestBase() {
    @Test
    fun `build creates record with static attributes`() {
        val definition =
            DefaultFactoryDefinition(
                recordClass = UsersRecord::class,
                attributes =
                    mapOf(
                        "name" to StaticAttribute("Test User"),
                        "email" to StaticAttribute("test@example.com"),
                        "age" to StaticAttribute(25),
                    ),
            )

        val builder = DefaultFactoryBuilder(dsl, definition)
        val user = builder.build()

        assertThat(user.name).isEqualTo("Test User")
        assertThat(user.email).isEqualTo("test@example.com")
        assertThat(user.age).isEqualTo(25)
    }

    @Test
    fun `build applies overrides`() {
        val definition =
            DefaultFactoryDefinition(
                recordClass = UsersRecord::class,
                attributes =
                    mapOf(
                        "name" to StaticAttribute("Default Name"),
                        "email" to StaticAttribute("default@example.com"),
                    ),
            )

        val builder = DefaultFactoryBuilder(dsl, definition)
        val user = builder.build(overrides = mapOf("name" to "Override Name"))

        assertThat(user.name).isEqualTo("Override Name")
        assertThat(user.email).isEqualTo("default@example.com")
    }

    @Test
    fun `build does not persist to database`() {
        val definition =
            DefaultFactoryDefinition(
                recordClass = UsersRecord::class,
                attributes =
                    mapOf(
                        "name" to StaticAttribute("Test User"),
                        "email" to StaticAttribute("test@example.com"),
                    ),
            )

        val builder = DefaultFactoryBuilder(dsl, definition)
        builder.build()

        val count = dsl.selectCount().from(Users.USERS).fetchOne(0, Int::class.java)
        assertThat(count).isEqualTo(0)
    }
}
