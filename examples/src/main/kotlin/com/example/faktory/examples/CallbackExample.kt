package com.example.faktory.examples

import com.example.faktory.dsl.factory
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import org.jooq.DSLContext
import org.jooq.Record
import java.time.LocalDateTime

class CallbackExample(private val dsl: DSLContext) {
    init {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    fun defineFactoryWithAfterBuild() {
        factory<UserRecord> {
            firstName = "John"
            lastName = "Doe"

            afterBuild { user, _ ->
                user.fullName = "${user.firstName} ${user.lastName}"
                println("Built user: ${user.fullName}")
            }
        }
    }

    fun defineFactoryWithBeforeCreate() {
        factory<UserRecord> {
            name = "User"
            email = "user@example.com"

            beforeCreate { user, _ ->
                user.createdAt = LocalDateTime.now()
                println("Creating user: ${user.name}")

                require(user.email.contains("@")) {
                    "Invalid email: ${user.email}"
                }
            }
        }
    }

    fun defineFactoryWithAfterCreate() {
        factory<UserRecord> {
            name = "User"
            email = "user@example.com"

            afterCreate { user, _ ->
                println("Created user with ID: ${user.id}")

                dsl.factory<PostRecord>().createList(5, mapOf(
                    "userId" to user.id
                ))
            }
        }
    }

    fun defineFactoryWithAllCallbacks() {
        val executionOrder = mutableListOf<String>()

        factory<UserRecord> {
            name = "User"
            email = "user@example.com"

            afterBuild { _, _ ->
                executionOrder.add("afterBuild")
            }

            beforeCreate { _, _ ->
                executionOrder.add("beforeCreate")
            }

            afterCreate { _, _ ->
                executionOrder.add("afterCreate")
            }
        }

        println("Callback execution order:")
        executionOrder.forEach { println("  - $it") }
    }

    fun defineFactoryWithMultipleCallbacks() {
        factory<UserRecord> {
            name = "User"

            afterCreate { user, _ ->
                println("Callback 1: Create profile")
                dsl.factory<ProfileRecord>().create(mapOf(
                    "userId" to user.id
                ))
            }

            afterCreate { user, _ ->
                println("Callback 2: Create settings")
                dsl.factory<SettingsRecord>().create(mapOf(
                    "userId" to user.id
                ))
            }

            afterCreate { user, _ ->
                println("Callback 3: Send welcome email")
            }
        }
    }

    fun defineFactoryWithTraitCallbacks() {
        factory<UserRecord> {
            name = "User"

            afterCreate { _, _ ->
                println("Base callback executed")
            }

            trait("withCallback") {
                afterCreate { _, _ ->
                    println("Trait callback executed")
                }
            }
        }
    }

    fun demonstrateCallbackOrder() {
        val user = dsl.factory<UserRecord>().create()
        println("User created with full lifecycle")
    }

    fun demonstrateCallbackWithBuildList() {
        var callbackCount = 0

        factory<UserRecord> {
            name = "User"

            afterCreate { _, _ ->
                callbackCount++
            }
        }

        dsl.factory<UserRecord>().createList(5)
        println("Callback executed $callbackCount times")
    }
}

interface UserRecord : Record {
    var id: Long?
    var name: String
    var email: String
    var firstName: String?
    var lastName: String?
    var fullName: String?
    var createdAt: LocalDateTime?
}

interface PostRecord : Record {
    var id: Long?
    var userId: Long
    var title: String
    var content: String
}

interface ProfileRecord : Record {
    var id: Long?
    var userId: Long
}

interface SettingsRecord : Record {
    var id: Long?
    var userId: Long
}

fun main() {
    println("Callback Example")
    println("================")
    println()
    println("Callbacks are lifecycle hooks that execute at specific points")
    println()
    println("Callback Types:")
    println("1. afterBuild - After record built (build/create)")
    println("   Use for: Computed fields, logging")
    println()
    println("2. beforeCreate - Before DB insert (create only)")
    println("   Use for: Validation, pre-processing, timestamps")
    println()
    println("3. afterCreate - After DB insert (create only)")
    println("   Use for: Create associations, audit logs")
    println()
    println("Execution Order:")
    println("  build() -> afterBuild")
    println("  create() -> afterBuild -> beforeCreate -> INSERT -> afterCreate")
    println()
    println("Callback Order with Traits:")
    println("  Base callbacks execute first, then trait callbacks")
    println()
    println("Multiple Callbacks:")
    println("  Multiple callbacks of same type execute in definition order")
    println()
    println("Best Practices:")
    println("- Keep callbacks simple and focused")
    println("- Use beforeCreate for validation")
    println("- Use afterCreate for associations")
    println("- Avoid side effects in afterBuild")
    println()
    println("See CallbackExampleTest.kt for working test examples")
}
