package com.example.faktory.builder

import com.example.faktory.core.DefaultFactoryDefinition
import com.example.faktory.core.StaticAttribute
import com.example.faktory.core.TraitDefinition
import com.example.faktory.test.JooqTestBase
import com.example.faktory.test.jooq.tables.Users
import com.example.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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

    @Test
    fun `create persists record to database`() {
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
        val user = builder.create()

        assertThat(user.name).isEqualTo("Test User")
        assertThat(user.email).isEqualTo("test@example.com")

        val count = dsl.selectCount().from(Users.USERS).fetchOne(0, Int::class.java)
        assertThat(count).isEqualTo(1)

        val found =
            dsl.selectFrom(Users.USERS)
                .where(Users.USERS.ID.eq(user.id))
                .fetchOne()

        assertThat(found).isNotNull
        assertThat(found?.name).isEqualTo("Test User")
        assertThat(found?.email).isEqualTo("test@example.com")
    }

    @Test
    fun `create applies overrides before persisting`() {
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
        val user = builder.create(overrides = mapOf("name" to "Custom Name"))

        assertThat(user.name).isEqualTo("Custom Name")

        val found =
            dsl.selectFrom(Users.USERS)
                .where(Users.USERS.ID.eq(user.id))
                .fetchOne()

        assertThat(found?.name).isEqualTo("Custom Name")
    }

    @Test
    fun `buildList creates multiple records without persisting`() {
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
        val users = builder.buildList(3)

        assertThat(users).hasSize(3)
        users.forEach { user ->
            assertThat(user.name).isEqualTo("Test User")
            assertThat(user.email).isEqualTo("test@example.com")
        }

        val count = dsl.selectCount().from(Users.USERS).fetchOne(0, Int::class.java)
        assertThat(count).isEqualTo(0)
    }

    @Test
    fun `createList persists multiple records to database`() {
        val definition =
            DefaultFactoryDefinition(
                recordClass = UsersRecord::class,
                attributes =
                    mapOf(
                        "name" to StaticAttribute("Test User"),
                    ),
            )

        val builder = DefaultFactoryBuilder(dsl, definition)
        val users = builder.createList(3, overrides = mapOf("email" to "unique-${System.nanoTime()}@example.com"))

        assertThat(users).hasSize(3)

        val count = dsl.selectCount().from(Users.USERS).fetchOne(0, Int::class.java)
        assertThat(count).isEqualTo(3)

        users.forEach { user ->
            val found =
                dsl.selectFrom(Users.USERS)
                    .where(Users.USERS.ID.eq(user.id))
                    .fetchOne()

            assertThat(found).isNotNull
            assertThat(found?.name).isEqualTo("Test User")
        }
    }

    @Test
    fun `build evaluates dynamic attributes`() {
        var counter = 0
        val definition =
            DefaultFactoryDefinition(
                recordClass = UsersRecord::class,
                attributes =
                    mapOf(
                        "name" to
                            com.example.faktory.core.DynamicAttribute { _ ->
                                "Dynamic User ${++counter}"
                            },
                        "email" to com.example.faktory.core.StaticAttribute("test@example.com"),
                    ),
            )

        val builder = DefaultFactoryBuilder(dsl, definition)
        val user1 = builder.build()
        val user2 = builder.build()

        assertThat(user1.name).isEqualTo("Dynamic User 1")
        assertThat(user2.name).isEqualTo("Dynamic User 2")
    }

    @Test
    fun `build evaluates sequence attributes`() {
        val definition =
            DefaultFactoryDefinition(
                recordClass = UsersRecord::class,
                attributes =
                    mapOf(
                        "name" to com.example.faktory.core.StaticAttribute("User"),
                        "email" to com.example.faktory.core.SequenceAttribute(null) { n -> "user$n@example.com" },
                    ),
            )

        val builder = DefaultFactoryBuilder(dsl, definition)
        val user1 = builder.build()
        val user2 = builder.build()
        val user3 = builder.build()

        assertThat(user1.email).matches("user\\d+@example.com")
        assertThat(user2.email).matches("user\\d+@example.com")
        assertThat(user3.email).matches("user\\d+@example.com")
        assertThat(user1.email).isNotEqualTo(user2.email)
        assertThat(user2.email).isNotEqualTo(user3.email)
    }

    @Test
    fun `build applies defaultTraits automatically`() {
        val definition =
            DefaultFactoryDefinition(
                recordClass = UsersRecord::class,
                defaultTraits = listOf("admin"),
                attributes =
                    mapOf(
                        "name" to StaticAttribute("Regular User"),
                        "email" to StaticAttribute("user@example.com"),
                        "age" to StaticAttribute(25),
                    ),
                traits =
                    mapOf(
                        "admin" to
                            TraitDefinition(
                                name = "admin",
                                attributes =
                                    mapOf(
                                        "name" to StaticAttribute("Admin User"),
                                        "age" to StaticAttribute(35),
                                    ),
                            ),
                    ),
            )

        val builder = DefaultFactoryBuilder(dsl, definition)
        val user = builder.build()

        assertThat(user.name).isEqualTo("Admin User")
        assertThat(user.age).isEqualTo(35)
        assertThat(user.email).isEqualTo("user@example.com")
    }

    @Test
    fun `create applies defaultTraits automatically`() {
        val definition =
            DefaultFactoryDefinition(
                recordClass = UsersRecord::class,
                defaultTraits = listOf("admin"),
                attributes =
                    mapOf(
                        "name" to StaticAttribute("Regular User"),
                        "email" to StaticAttribute("user@example.com"),
                        "age" to StaticAttribute(25),
                    ),
                traits =
                    mapOf(
                        "admin" to
                            TraitDefinition(
                                name = "admin",
                                attributes =
                                    mapOf(
                                        "name" to StaticAttribute("Admin User"),
                                        "age" to StaticAttribute(35),
                                    ),
                            ),
                    ),
            )

        val builder = DefaultFactoryBuilder(dsl, definition)
        val user = builder.create()

        assertThat(user.name).isEqualTo("Admin User")
        assertThat(user.age).isEqualTo(35)

        val found = dsl.selectFrom(Users.USERS).where(Users.USERS.ID.eq(user.id)).fetchOne()
        assertThat(found?.name).isEqualTo("Admin User")
        assertThat(found?.age).isEqualTo(35)
    }

    @Test
    fun `build applies multiple defaultTraits in order`() {
        val definition =
            DefaultFactoryDefinition(
                recordClass = UsersRecord::class,
                defaultTraits = listOf("admin", "senior"),
                attributes =
                    mapOf(
                        "name" to StaticAttribute("Regular User"),
                        "email" to StaticAttribute("user@example.com"),
                        "age" to StaticAttribute(25),
                    ),
                traits =
                    mapOf(
                        "admin" to
                            TraitDefinition(
                                name = "admin",
                                attributes = mapOf("name" to StaticAttribute("Admin User")),
                            ),
                        "senior" to
                            TraitDefinition(
                                name = "senior",
                                attributes = mapOf("age" to StaticAttribute(65)),
                            ),
                    ),
            )

        val builder = DefaultFactoryBuilder(dsl, definition)
        val user = builder.build()

        assertThat(user.name).isEqualTo("Admin User")
        assertThat(user.age).isEqualTo(65)
    }
}
