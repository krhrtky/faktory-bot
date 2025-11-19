package io.github.krhrtky.faktory.examples

import io.github.krhrtky.faktory.dsl.factory
import io.github.krhrtky.faktory.examples.jooq.tables.Users.Companion.USERS
import io.github.krhrtky.faktory.examples.jooq.tables.records.UsersRecord
import io.github.krhrtky.faktory.registry.GlobalFactoryRegistry
import io.github.krhrtky.faktory.sequence.GlobalSequenceManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CallbackExample : ExamplesTestBase() {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    @Test
    fun `1 - afterBuild callback modifies in-memory record`() {
        val builtUsers = mutableListOf<String>()

        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25

            afterBuild { user, _ ->
                user.name?.let { builtUsers.add(it) }
            }
        }

        val user = dsl.factory<UsersRecord>().build()

        assertThat(user.name).isEqualTo("User")
        assertThat(builtUsers).containsExactly("User")
    }

    @Test
    fun `2 - afterCreate callback executes after persistence`() {
        val createdUserIds = mutableListOf<Long>()

        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set sequence { n -> "user$n@example.com" }
            USERS.AGE set 25

            afterCreate { user, _ ->
                user.id?.let { createdUserIds.add(it) }
            }
        }

        val users = dsl.factory<UsersRecord>().createList(3)

        assertThat(users).hasSize(3)
        assertThat(users.all { it.id != null }).isTrue()
        assertThat(createdUserIds).hasSize(3)
        assertThat(createdUserIds).containsExactlyElementsOf(users.mapNotNull { it.id })
    }

    @Test
    fun `3 - Multiple callbacks execute in order`() {
        val executionLog = mutableListOf<String>()

        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25

            afterBuild { _, _ ->
                executionLog.add("afterBuild")
            }

            beforeCreate { _, _ ->
                executionLog.add("beforeCreate")
            }

            afterCreate { _, _ ->
                executionLog.add("afterCreate")
            }
        }

        dsl.factory<UsersRecord>().create()

        assertThat(executionLog).containsExactly(
            "afterBuild",
            "beforeCreate",
            "afterCreate",
        )
    }
}
