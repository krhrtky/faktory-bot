package com.example.faktory.examples

import com.example.faktory.dsl.factory
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import org.jooq.DSLContext
import org.jooq.Record

class BasicExample(private val dsl: DSLContext) {
    init {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    fun defineUserFactory() {
        factory<UserRecord> {
            name = "John Doe"
            email = "john@example.com"
            age = 30
        }
    }

    fun buildUser(): UserRecord {
        return dsl.factory<UserRecord>().build()
    }

    fun buildUserWithOverrides(): UserRecord {
        return dsl.factory<UserRecord>().build(mapOf(
            "name" to "Jane Smith",
            "age" to 25
        ))
    }

    fun createUser(): UserRecord {
        return dsl.factory<UserRecord>().create()
    }

    fun createUserWithOverrides(): UserRecord {
        return dsl.factory<UserRecord>().create(mapOf(
            "email" to "custom@example.com"
        ))
    }

    fun buildUserList(): List<UserRecord> {
        return dsl.factory<UserRecord>().buildList(10)
    }

    fun createUserList(): List<UserRecord> {
        return dsl.factory<UserRecord>().createList(10)
    }

    fun getUserAttributes(): Map<String, Any?> {
        return dsl.factory<UserRecord>().attributes()
    }

    fun getUserAttributesWithOverrides(): Map<String, Any?> {
        return dsl.factory<UserRecord>().attributes(mapOf(
            "name" to "Custom User"
        ))
    }
}

interface UserRecord : Record {
    var id: Long?
    var name: String
    var email: String
    var age: Int
}

fun main() {
    println("Basic Factory Example")
    println("=====================")
    println()
    println("1. Define factory with default attributes")
    println("2. build() - Create in-memory record")
    println("3. build(overrides) - Create with custom attributes")
    println("4. create() - Create and persist to database")
    println("5. create(overrides) - Create with custom attributes and persist")
    println("6. buildList(n) - Create multiple in-memory records")
    println("7. createList(n) - Create and persist multiple records")
    println("8. attributes() - Get evaluated attributes as Map")
    println("9. attributes(overrides) - Get evaluated attributes with overrides")
    println()
    println("See BasicExampleTest.kt for working test examples")
}
