package com.example.faktory.examples

import com.example.faktory.dsl.factory
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import org.jooq.DSLContext
import org.jooq.Record

class TransientExample(private val dsl: DSLContext) {
    init {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    fun defineFactoryWithTransients() {
        factory<UserRecord> {
            name = "User"
            email = "user@example.com"

            transient {
                set("postsCount", 5)
                set("verified", true)
            }

            afterCreate { user, transients ->
                val count = transients.getOrNull("postsCount") as? Int ?: 0
                repeat(count) {
                    dsl.factory<PostRecord>().create(mapOf(
                        "userId" to user.id
                    ))
                }

                val verified = transients.getOrNull("verified") as? Boolean ?: false
                if (verified) {
                    println("User is verified")
                }
            }
        }
    }

    fun defineFactoryWithConditionalBehavior() {
        factory<UserRecord> {
            name = "User"
            email = "user@example.com"

            transient {
                set("sendWelcomeEmail", false)
                set("createProfile", true)
            }

            afterCreate { user, transients ->
                val sendEmail = transients.getOrNull("sendWelcomeEmail") as? Boolean ?: false
                if (sendEmail) {
                    println("Sending welcome email to ${user.email}")
                }

                val createProfile = transients.getOrNull("createProfile") as? Boolean ?: false
                if (createProfile) {
                    dsl.factory<ProfileRecord>().create(mapOf(
                        "userId" to user.id
                    ))
                }
            }
        }
    }

    fun defineFactoryWithTraitTransients() {
        factory<UserRecord> {
            name = "User"
            email = "user@example.com"

            transient {
                set("postsCount", 0)
            }

            trait("withPosts") {
                transient {
                    set("postsCount", 5)
                }
            }

            trait("withManyPosts") {
                transient {
                    set("postsCount", 50)
                }
            }

            afterCreate { user, transients ->
                val count = transients.getOrNull("postsCount") as? Int ?: 0
                repeat(count) {
                    dsl.factory<PostRecord>().create(mapOf(
                        "userId" to user.id
                    ))
                }
            }
        }
    }

    fun createUserWithDefaultTransients(): UserRecord {
        return dsl.factory<UserRecord>().create()
    }

    fun createUserWithOverriddenTransients(): UserRecord {
        return dsl.factory<UserRecord>().create(mapOf(
            "postsCount" to 10
        ))
    }

    fun createUserWithMultipleTransients(): UserRecord {
        return dsl.factory<UserRecord>().create(mapOf(
            "sendWelcomeEmail" to true,
            "createProfile" to true
        ))
    }

    fun demonstrateTransientTypes() {
        factory<UserRecord> {
            name = "User"

            transient {
                set("intValue", 42)
                set("stringValue", "test")
                set("boolValue", true)
                set("listValue", listOf("a", "b", "c"))
                set("mapValue", mapOf("key" to "value"))
            }

            afterCreate { user, transients ->
                val intVal = transients.getOrNull("intValue") as? Int
                val strVal = transients.getOrNull("stringValue") as? String
                val boolVal = transients.getOrNull("boolValue") as? Boolean
                val listVal = transients.getOrNull("listValue") as? List<*>
                val mapVal = transients.getOrNull("mapValue") as? Map<*, *>

                println("Int: $intVal")
                println("String: $strVal")
                println("Boolean: $boolVal")
                println("List: $listVal")
                println("Map: $mapVal")
            }
        }
    }

    fun demonstrateTransientHelpers() {
        fun getInt(transients: Map<String, Any?>, key: String, default: Int = 0): Int =
            transients[key] as? Int ?: default

        fun getBool(transients: Map<String, Any?>, key: String, default: Boolean = false): Boolean =
            transients[key] as? Boolean ?: default

        factory<UserRecord> {
            name = "User"

            transient {
                set("postsCount", 5)
                set("sendEmail", true)
            }

            afterCreate { user, transients ->
                val count = getInt(transients as Map<String, Any?>, "postsCount", 0)
                val sendEmail = getBool(transients as Map<String, Any?>, "sendEmail", false)

                println("Posts count: $count, Send email: $sendEmail")
            }
        }
    }
}

interface UserRecord : Record {
    var id: Long?
    var name: String
    var email: String
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

fun main() {
    println("Transient Example")
    println("=================")
    println()
    println("Transients are temporary attributes that control callback behavior")
    println("without being persisted to the database")
    println()
    println("Use Cases:")
    println("1. Control number of associations to create")
    println("2. Enable/disable conditional behavior")
    println("3. Pass test metadata to callbacks")
    println("4. Configure dynamic data generation")
    println()
    println("Defining Transients:")
    println("  transient {")
    println("    set(\"postsCount\", 5)")
    println("    set(\"sendEmail\", true)")
    println("  }")
    println()
    println("Accessing Transients:")
    println("  afterCreate { user, transients ->")
    println("    val count = transients.getOrNull(\"postsCount\") as? Int ?: 0")
    println("  }")
    println()
    println("Overriding Transients:")
    println("  dsl.factory<UserRecord>().create(mapOf(")
    println("    \"postsCount\" to 10")
    println("  ))")
    println()
    println("Transients vs Attributes:")
    println("- Transients: Not persisted, used in callbacks, any type")
    println("- Attributes: Persisted to DB, match jOOQ Record fields, type-checked")
    println()
    println("Best Practices:")
    println("- Use descriptive names")
    println("- Provide defaults in callbacks")
    println("- Document transient behavior")
    println("- Use traits for common configurations")
    println()
    println("See TransientExampleTest.kt for working test examples")
}
