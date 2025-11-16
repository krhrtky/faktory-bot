package com.example.faktory.integration

import com.example.faktory.builder.DefaultFactoryBuilder
import com.example.faktory.core.DefaultFactoryDefinition
import com.example.faktory.core.StaticAttribute
import com.example.faktory.dsl.globalTrait
import com.example.faktory.registry.GlobalTraitRegistry
import com.example.faktory.test.JooqTestBase
import com.example.faktory.test.jooq.tables.Users
import com.example.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class GlobalTraitIntegrationTest : JooqTestBase() {
    @BeforeEach
    fun setup() {
        GlobalTraitRegistry.clear()
    }

    @Test
    fun `factory can use global trait via defaultTraits`() {
        globalTrait(UsersRecord::class, "timestamps") {
            attribute("created_at", LocalDateTime.of(2024, 1, 1, 0, 0))
            attribute("updated_at", LocalDateTime.of(2024, 1, 1, 0, 0))
        }

        val definition =
            DefaultFactoryDefinition(
                recordClass = UsersRecord::class,
                defaultTraits = listOf("timestamps"),
                attributes =
                    mapOf(
                        "name" to StaticAttribute("Test User"),
                        "email" to StaticAttribute("test@example.com"),
                    ),
            )

        val builder = DefaultFactoryBuilder(dsl, definition)
        val user = builder.build()

        assertThat(user.name).isEqualTo("Test User")
        assertThat(user.createdAt).isEqualTo(LocalDateTime.of(2024, 1, 1, 0, 0))
        assertThat(user.updatedAt).isEqualTo(LocalDateTime.of(2024, 1, 1, 0, 0))
    }

    @Test
    fun `factory can use multiple global traits`() {
        globalTrait(UsersRecord::class, "timestamps") {
            attribute("created_at", LocalDateTime.of(2024, 1, 1, 0, 0))
        }

        globalTrait(UsersRecord::class, "soft_delete") {
            attribute("updated_at", LocalDateTime.of(2024, 12, 31, 23, 59))
        }

        val definition =
            DefaultFactoryDefinition(
                recordClass = UsersRecord::class,
                defaultTraits = listOf("timestamps", "soft_delete"),
                attributes =
                    mapOf(
                        "name" to StaticAttribute("Test User"),
                        "email" to StaticAttribute("test@example.com"),
                    ),
            )

        val builder = DefaultFactoryBuilder(dsl, definition)
        val user = builder.build()

        assertThat(user.createdAt).isEqualTo(LocalDateTime.of(2024, 1, 1, 0, 0))
        assertThat(user.updatedAt).isEqualTo(LocalDateTime.of(2024, 12, 31, 23, 59))
    }

    @Test
    fun `global trait and local trait can be combined`() {
        globalTrait(UsersRecord::class, "timestamps") {
            attribute("created_at", LocalDateTime.of(2024, 1, 1, 0, 0))
        }

        val definition =
            DefaultFactoryDefinition(
                recordClass = UsersRecord::class,
                defaultTraits = listOf("timestamps", "admin"),
                attributes =
                    mapOf(
                        "name" to StaticAttribute("Regular User"),
                        "email" to StaticAttribute("test@example.com"),
                        "age" to StaticAttribute(25),
                    ),
                traits =
                    mapOf(
                        "admin" to
                            com.example.faktory.core.TraitDefinition(
                                name = "admin",
                                attributes = mapOf("name" to StaticAttribute("Admin User")),
                            ),
                    ),
            )

        val builder = DefaultFactoryBuilder(dsl, definition)
        val user = builder.build()

        assertThat(user.name).isEqualTo("Admin User")
        assertThat(user.createdAt).isEqualTo(LocalDateTime.of(2024, 1, 1, 0, 0))
    }

    @Test
    fun `create with global trait persists to database`() {
        globalTrait(UsersRecord::class, "timestamps") {
            attribute("created_at", LocalDateTime.of(2024, 1, 1, 0, 0))
            attribute("updated_at", LocalDateTime.of(2024, 1, 1, 0, 0))
        }

        val definition =
            DefaultFactoryDefinition(
                recordClass = UsersRecord::class,
                defaultTraits = listOf("timestamps"),
                attributes =
                    mapOf(
                        "name" to StaticAttribute("Test User"),
                        "email" to StaticAttribute("test@example.com"),
                    ),
            )

        val builder = DefaultFactoryBuilder(dsl, definition)
        val user = builder.create()

        assertThat(user.createdAt).isEqualTo(LocalDateTime.of(2024, 1, 1, 0, 0))

        val found = dsl.selectFrom(Users.USERS).where(Users.USERS.ID.eq(user.id)).fetchOne()
        assertThat(found?.createdAt).isEqualTo(LocalDateTime.of(2024, 1, 1, 0, 0))
    }
}
