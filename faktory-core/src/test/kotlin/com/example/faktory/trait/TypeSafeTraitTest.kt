package com.example.faktory.trait

import com.example.faktory.core.Trait
import com.example.faktory.dsl.factory
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import com.example.faktory.test.JooqTestBase
import com.example.faktory.test.jooq.tables.Users.Companion.USERS
import com.example.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

sealed class UserTrait : Trait<UsersRecord> {
    data object Admin : UserTrait() {
        override val name = "admin"
    }

    data object Premium : UserTrait() {
        override val name = "premium"
    }

    data object Guest : UserTrait() {
        override val name = "guest"
    }
}

class TypeSafeTraitTest : JooqTestBase() {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    @Test
    fun `define trait with Trait interface`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25

            trait(UserTrait.Admin) {
                USERS.NAME set "Admin User"
                USERS.AGE set 35
            }
        }

        val regularUser = dsl.factory<UsersRecord>().build()
        val adminUser = dsl.factory<UsersRecord>().build(UserTrait.Admin)

        assertThat(regularUser.name).isEqualTo("User")
        assertThat(regularUser.age).isEqualTo(25)

        assertThat(adminUser.name).isEqualTo("Admin User")
        assertThat(adminUser.age).isEqualTo(35)
    }

    @Test
    fun `define multiple traits with sealed class`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set sequence { n -> "user$n@example.com" }
            USERS.AGE set 25

            trait(UserTrait.Admin) {
                USERS.NAME set "Admin User"
                USERS.AGE set 35
            }

            trait(UserTrait.Premium) {
                USERS.NAME set "Premium User"
                USERS.AGE set 30
            }

            trait(UserTrait.Guest) {
                USERS.NAME set "Guest User"
                USERS.AGE set 20
            }
        }

        val admin = dsl.factory<UsersRecord>().build(UserTrait.Admin)
        val premium = dsl.factory<UsersRecord>().build(UserTrait.Premium)
        val guest = dsl.factory<UsersRecord>().build(UserTrait.Guest)

        assertThat(admin.name).isEqualTo("Admin User")
        assertThat(admin.age).isEqualTo(35)

        assertThat(premium.name).isEqualTo("Premium User")
        assertThat(premium.age).isEqualTo(30)

        assertThat(guest.name).isEqualTo("Guest User")
        assertThat(guest.age).isEqualTo(20)
    }

    @Test
    fun `type safety prevents wrong record type traits`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25

            trait(UserTrait.Admin) {
                USERS.NAME set "Admin User"
            }
        }

        val admin = dsl.factory<UsersRecord>().create(UserTrait.Admin)

        assertThat(admin.name).isEqualTo("Admin User")
    }
}
