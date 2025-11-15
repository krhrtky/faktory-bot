package com.example.faktory.examples

import com.example.faktory.dsl.factory
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import org.jooq.DSLContext
import org.jooq.Record

class TraitExample(private val dsl: DSLContext) {
    init {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    fun defineFactoryWithRoleTraits() {
        factory<UserRecord> {
            name = "Regular User"
            email = "user@example.com"
            age = 25
            role = "USER"

            trait("admin") {
                attribute("name", "Admin User")
                attribute("role", "ADMIN")
                attribute("age", 35)
            }

            trait("moderator") {
                attribute("role", "MODERATOR")
                attribute("age", 30)
            }

            trait("guest") {
                attribute("role", "GUEST")
            }
        }
    }

    fun defineFactoryWithStatusTraits() {
        factory<OrderRecord> {
            status = "PENDING"
            total = 100

            trait("completed") {
                attribute("status", "COMPLETED")
            }

            trait("cancelled") {
                attribute("status", "CANCELLED")
            }

            trait("shipped") {
                attribute("status", "SHIPPED")
            }
        }
    }

    fun defineFactoryWithFlagTraits() {
        factory<UserRecord> {
            name = "User"
            email = sequence { n -> "user${n}@example.com" }
            isActive = true
            isVerified = false
            isPremium = false

            trait("verified") {
                attribute("isVerified", true)
            }

            trait("premium") {
                attribute("isPremium", true)
            }

            trait("inactive") {
                attribute("isActive", false)
            }
        }
    }

    fun defineFactoryWithCallbackTrait() {
        var callbackExecuted = false

        factory<UserRecord> {
            name = "User"
            email = "user@example.com"

            trait("withCallback") {
                afterCreate { user, _ ->
                    callbackExecuted = true
                    println("Callback executed for user: ${user.name}")
                }
            }
        }
    }

    fun buildRegularUser(): UserRecord {
        return dsl.factory<UserRecord>().build()
    }

    fun buildAdmin(): UserRecord {
        return dsl.factory<UserRecord>().build("admin")
    }

    fun buildMultipleTraits(): UserRecord {
        return dsl.factory<UserRecord>().build("verified", "premium")
    }

    fun demonstrateTraitOverride() {
        val user1 = dsl.factory<UserRecord>().build("admin", "moderator")
        println("admin then moderator: role=${user1.role}")

        val user2 = dsl.factory<UserRecord>().build("moderator", "admin")
        println("moderator then admin: role=${user2.role}")
    }

    fun demonstrateTraitWithOverride() {
        val user = dsl.factory<UserRecord>().build("admin", overrides = mapOf(
            "age" to 45
        ))
        println("Admin with custom age: role=${user.role}, age=${user.age}")
    }
}

interface UserRecord : Record {
    var id: Long?
    var name: String
    var email: String
    var age: Int
    var role: String
    var isActive: Boolean
    var isVerified: Boolean
    var isPremium: Boolean
}

interface OrderRecord : Record {
    var id: Long?
    var status: String
    var total: Int
}

fun main() {
    println("Trait Example")
    println("=============")
    println()
    println("Traits define attribute variations without creating separate factories")
    println()
    println("Usage Patterns:")
    println("1. Role-based traits: admin, moderator, guest")
    println("2. Status-based traits: completed, cancelled, shipped")
    println("3. Flag-based traits: verified, premium, inactive")
    println("4. Traits with callbacks: custom logic per variation")
    println()
    println("Trait Composition:")
    println("- Single trait: build(\"admin\")")
    println("- Multiple traits: build(\"verified\", \"premium\")")
    println("- Trait order matters: later traits override earlier ones")
    println("- Overrides win: build(\"admin\", overrides = mapOf(\"age\" to 45))")
    println()
    println("Best Practices:")
    println("- Keep traits focused on single responsibility")
    println("- Use descriptive trait names")
    println("- Combine traits for complex scenarios")
    println()
    println("See TraitExampleTest.kt for working test examples")
}
