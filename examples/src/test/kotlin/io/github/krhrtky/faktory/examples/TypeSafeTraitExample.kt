package io.github.krhrtky.faktory.examples

import io.github.krhrtky.faktory.core.Trait
import io.github.krhrtky.faktory.dsl.factory
import io.github.krhrtky.faktory.examples.jooq.tables.references.USERS
import io.github.krhrtky.faktory.examples.jooq.tables.records.UsersRecord
import io.github.krhrtky.faktory.registry.GlobalFactoryRegistry
import io.github.krhrtky.faktory.sequence.GlobalSequenceManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

sealed class UserRole : Trait<UsersRecord> {
    data object Admin : UserRole() {
        override val name = "admin"
    }

    data object Premium : UserRole() {
        override val name = "premium"
    }

    data object Guest : UserRole() {
        override val name = "guest"
    }
}

sealed class UserStatus : Trait<UsersRecord> {
    data object Active : UserStatus() {
        override val name = "active"
    }

    data object Inactive : UserStatus() {
        override val name = "inactive"
    }
}

class TypeSafeTraitExample : ExamplesTestBase() {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    @Test
    fun `define traits using sealed class for type safety`() {
        factory<UsersRecord> {
            USERS.NAME set "Regular User"
            USERS.EMAIL set sequence { n -> "user${n}@example.com" }
            USERS.AGE set 25

            trait(UserRole.Admin) {
                USERS.NAME set "Admin User"
                USERS.AGE set 35
            }

            trait(UserRole.Premium) {
                USERS.NAME set "Premium User"
                USERS.AGE set 30
            }

            trait(UserRole.Guest) {
                USERS.NAME set "Guest User"
                USERS.AGE set 18
            }
        }

        val regular = dsl.factory<UsersRecord>().create()
        val admin = dsl.factory<UsersRecord>().create(UserRole.Admin)
        val premium = dsl.factory<UsersRecord>().create(UserRole.Premium)
        val guest = dsl.factory<UsersRecord>().create(UserRole.Guest)

        assertThat(regular.name).isEqualTo("Regular User")
        assertThat(admin.name).isEqualTo("Admin User")
        assertThat(premium.name).isEqualTo("Premium User")
        assertThat(guest.name).isEqualTo("Guest User")
    }

    @Test
    fun `combine multiple type-safe traits`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set sequence { n -> "user${n}@example.com" }
            USERS.AGE set 25

            trait(UserRole.Admin) {
                USERS.NAME set "Admin User"
            }

            trait(UserStatus.Active) {
                USERS.AGE set 30
            }

            trait(UserStatus.Inactive) {
                USERS.AGE set 0
            }
        }

        val activeAdmin = dsl.factory<UsersRecord>().create(UserRole.Admin, UserStatus.Active)
        val inactiveAdmin = dsl.factory<UsersRecord>().create(UserRole.Admin, UserStatus.Inactive)

        assertThat(activeAdmin.name).isEqualTo("Admin User")
        assertThat(activeAdmin.age).isEqualTo(30)

        assertThat(inactiveAdmin.name).isEqualTo("Admin User")
        assertThat(inactiveAdmin.age).isEqualTo(0)
    }

    @Test
    fun `IDE autocomplete suggests available traits`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25

            trait(UserRole.Admin) {
                USERS.NAME set "Admin"
            }

            trait(UserRole.Premium) {
                USERS.NAME set "Premium"
            }
        }

        val admin = dsl.factory<UsersRecord>().create(UserRole.Admin)

        assertThat(admin.name).isEqualTo("Admin")
    }

    @Test
    fun `prevent typos with compile-time validation`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25

            trait(UserRole.Admin) {
                USERS.NAME set "Admin"
            }
        }

        val admin = dsl.factory<UsersRecord>().create(UserRole.Admin)

        assertThat(admin.name).isEqualTo("Admin")
    }

    @Test
    fun `demonstrates strong typing with generics`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25

            trait(UserRole.Admin) {
                USERS.NAME set "Admin User"
            }

            trait(UserRole.Premium) {
                USERS.NAME set "Premium User"
            }
        }

        val admin = dsl.factory<UsersRecord>().create(UserRole.Admin)
        val premium = dsl.factory<UsersRecord>().create(UserRole.Premium)

        assertThat(admin.name).isEqualTo("Admin User")
        assertThat(premium.name).isEqualTo("Premium User")
    }
}
