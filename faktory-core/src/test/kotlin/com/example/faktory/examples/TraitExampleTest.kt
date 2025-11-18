package com.example.faktory.examples
import com.example.faktory.dsl.factory
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import com.example.faktory.test.CommonUserTrait
import com.example.faktory.test.JooqTestBase
import com.example.faktory.test.jooq.tables.Users.Companion.USERS
import com.example.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TraitExampleTest : JooqTestBase() {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    @Test
    fun `1 - Single trait overrides base attributes`() {
        factory<UsersRecord> {
            USERS.NAME set "Regular User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25

            trait(CommonUserTrait.Admin) {
                USERS.NAME set "Admin User"
                USERS.AGE set 35
            }
        }

        val regularUser = dsl.factory<UsersRecord>().build()
        val adminUser = dsl.factory<UsersRecord>().build(CommonUserTrait.Admin)

        assertThat(regularUser.name).isEqualTo("Regular User")
        assertThat(regularUser.age).isEqualTo(25)

        assertThat(adminUser.name).isEqualTo("Admin User")
        assertThat(adminUser.email).isEqualTo("user@example.com")
        assertThat(adminUser.age).isEqualTo(35)
    }

    @Test
    fun `2 - Multiple traits can be composed`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set sequence { n -> "user$n@example.com" }
            USERS.AGE set 25

            trait(CommonUserTrait.Verified) {
                USERS.EMAIL set "verified@example.com"
            }

            trait(CommonUserTrait.Senior) {
                USERS.AGE set 60
            }
        }

        val user = dsl.factory<UsersRecord>().build(CommonUserTrait.Verified, CommonUserTrait.Senior)

        assertThat(user.name).isEqualTo("User")
        assertThat(user.email).isEqualTo("verified@example.com")
        assertThat(user.age).isEqualTo(60)
    }

    @Test
    fun `3 - Trait order matters - later traits override earlier ones`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25

            trait(CommonUserTrait.Young) {
                USERS.AGE set 18
            }

            trait(CommonUserTrait.Old) {
                USERS.AGE set 70
            }
        }

        val user1 = dsl.factory<UsersRecord>().build(CommonUserTrait.Young, CommonUserTrait.Old)
        assertThat(user1.age).isEqualTo(70)

        val user2 = dsl.factory<UsersRecord>().build(CommonUserTrait.Old, CommonUserTrait.Young)
        assertThat(user2.age).isEqualTo(18)
    }

    @Test
    fun `4 - Traits with callbacks`() {
        var callbackExecuted = false

        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25

            trait(CommonUserTrait.WithCallback) {
                afterCreate { _, _ ->
                    callbackExecuted = true
                }
            }
        }

        dsl.factory<UsersRecord>().create(CommonUserTrait.WithCallback)

        assertThat(callbackExecuted).isTrue()
    }

    @Test
    fun `5 - Traits override default values`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25

            trait(CommonUserTrait.Senior) {
                USERS.AGE set 60
            }
        }

        val user = dsl.factory<UsersRecord>().build(CommonUserTrait.Senior)

        assertThat(user.age).isEqualTo(60)
    }

    @Test
    fun `6 - Flag-based traits for feature toggles`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set sequence { n -> "user$n@example.com" }
            USERS.AGE set 25

            trait(CommonUserTrait.Verified) {
                USERS.NAME set "Verified User"
            }

            trait(CommonUserTrait.Premium) {
                USERS.NAME set "Premium User"
            }
        }

        val regular = dsl.factory<UsersRecord>().build()
        val verified = dsl.factory<UsersRecord>().build(CommonUserTrait.Verified)
        val premium = dsl.factory<UsersRecord>().build(CommonUserTrait.Premium)

        assertThat(regular.name).isEqualTo("User")
        assertThat(verified.name).isEqualTo("Verified User")
        assertThat(premium.name).isEqualTo("Premium User")
    }

    @Test
    fun `7 - Traits work with create() and persist to database`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25

            trait(CommonUserTrait.Admin) {
                USERS.NAME set "Admin User"
            }
        }

        val admin = dsl.factory<UsersRecord>().create(CommonUserTrait.Admin)

        assertThat(admin.id).isNotNull()
        assertThat(admin.name).isEqualTo("Admin User")
    }

    @Test
    fun `8 - Multiple traits with callbacks execute in order`() {
        val executionOrder = mutableListOf<String>()

        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25

            afterCreate { _, _ ->
                executionOrder.add("base")
            }

            trait(CommonUserTrait.T1) {
                afterCreate { _, _ ->
                    executionOrder.add("t1")
                }
            }

            trait(CommonUserTrait.T2) {
                afterCreate { _, _ ->
                    executionOrder.add("t2")
                }
            }
        }

        dsl.factory<UsersRecord>().create(CommonUserTrait.T1, CommonUserTrait.T2)

        assertThat(executionOrder).containsExactly("base", "t1", "t2")
    }
}
