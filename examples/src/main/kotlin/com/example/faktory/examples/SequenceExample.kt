package com.example.faktory.examples

import com.example.faktory.dsl.factory
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import org.jooq.DSLContext
import org.jooq.Record

class SequenceExample(private val dsl: DSLContext) {
    init {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    fun defineFactoryWithSimpleSequence() {
        factory<UserRecord> {
            name = "User"
            email = sequence { n -> "user${n}@example.com" }
            age = 25
        }
    }

    fun defineFactoryWithFormattedSequence() {
        factory<ProductRecord> {
            name = "Product"
            sku = sequence { n -> "PROD-${String.format("%06d", n)}" }
            price = 100
        }
    }

    fun defineFactoryWithMultipleSequences() {
        factory<UserRecord> {
            name = sequence { n -> "User $n" }
            email = sequence { n -> "user${n}@example.com" }
            age = sequence { n -> 20 + n }
        }
    }

    fun defineFactoryWithPhoneSequence() {
        factory<UserRecord> {
            name = "User"
            email = sequence { n -> "user${n}@example.com" }
            phone = sequence { n -> "+1-555-${String.format("%04d", n)}" }
        }
    }

    fun buildUsers(): List<UserRecord> {
        return dsl.factory<UserRecord>().buildList(5)
    }

    fun demonstrateSequenceUniqueness() {
        val user1 = dsl.factory<UserRecord>().build()
        val user2 = dsl.factory<UserRecord>().build()
        val user3 = dsl.factory<UserRecord>().build()

        println("User 1 email: ${user1.email}")
        println("User 2 email: ${user2.email}")
        println("User 3 email: ${user3.email}")

        val emails = setOf(user1.email, user2.email, user3.email)
        println("Unique emails: ${emails.size}")
    }

    fun demonstrateSequenceReset() {
        val beforeReset = dsl.factory<UserRecord>().build()
        println("Before reset: ${beforeReset.email}")

        GlobalSequenceManager.reset()

        val afterReset = dsl.factory<UserRecord>().build()
        println("After reset: ${afterReset.email}")
    }
}

interface ProductRecord : Record {
    var id: Long?
    var name: String
    var sku: String
    var price: Int
}

interface UserRecord : Record {
    var id: Long?
    var name: String
    var email: String
    var age: Int
    var phone: String?
}

fun main() {
    println("Sequence Example")
    println("================")
    println()
    println("Sequences generate unique values for factory attributes")
    println()
    println("Patterns:")
    println("1. Simple sequence: sequence { n -> \"user\${n}@example.com\" }")
    println("2. Formatted sequence: sequence { n -> \"PROD-\${String.format(\"%06d\", n)}\" }")
    println("3. Phone sequence: sequence { n -> \"+1-555-\${String.format(\"%04d\", n)}\" }")
    println("4. Multiple sequences: Each attribute has independent counter")
    println()
    println("Best Practices:")
    println("- Always reset sequences in @BeforeEach: GlobalSequenceManager.reset()")
    println("- Use descriptive patterns for clarity")
    println("- Format numbers with padding for sortability")
    println()
    println("See SequenceExampleTest.kt for working test examples")
}
