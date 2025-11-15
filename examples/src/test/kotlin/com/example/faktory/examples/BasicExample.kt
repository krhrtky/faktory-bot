package com.example.faktory.examples

import com.example.faktory.dsl.factory
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import com.example.faktory.examples.jooq.tables.Users
import com.example.faktory.examples.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Basic Faktory Bot usage examples.
 *
 * This demonstrates the core features of the library:
 * - Defining factories
 * - Building in-memory records
 * - Creating and persisting records to database
 * - Working with lists
 * - Extracting attributes as maps
 */
class BasicExample : ExamplesTestBase() {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    @Test
    fun `define a factory with default attributes`() {
        // Define a factory for UserRecord with default values
        factory<UsersRecord> {
            name = "John Doe"
            email = "john@example.com"
            age = 30
        }

        // Build an in-memory record using the factory
        val user = dsl.factory<UsersRecord>().build()

        // Verify the attributes
        assertThat(user.name).isEqualTo("John Doe")
        assertThat(user.email).isEqualTo("john@example.com")
        assertThat(user.age).isEqualTo(30)
    }

    @Test
    fun `build() creates in-memory record without database persistence`() {
        factory<UsersRecord> {
            name = "John Doe"
            email = "john@example.com"
            age = 30
        }

        // build() creates the record but doesn't save to database
        val user = dsl.factory<UsersRecord>().build()

        // ID is null because record is not persisted
        assertThat(user.id).isNull()
        assertThat(user.name).isEqualTo("John Doe")
    }

    @Test
    fun `override attributes when building records`() {
        factory<UsersRecord> {
            name = "John Doe"
            email = "john@example.com"
            age = 30
        }

        // Override specific attributes using a map
        val user = dsl.factory<UsersRecord>().build(mapOf(
            "name" to "Jane Smith",
            "age" to 25
        ))

        // Overridden values are used
        assertThat(user.name).isEqualTo("Jane Smith")
        assertThat(user.age).isEqualTo(25)

        // Non-overridden values use factory defaults
        assertThat(user.email).isEqualTo("john@example.com")
    }

    @Test
    fun `create() persists record to database`() {
        factory<UsersRecord> {
            name = "John Doe"
            email = "john@example.com"
            age = 30
        }

        // create() builds and persists the record
        val user = dsl.factory<UsersRecord>().create()

        // ID is assigned by database
        assertThat(user.id).isNotNull()
        assertThat(user.name).isEqualTo("John Doe")

        // Verify record exists in database
        val found = dsl.selectFrom(Users.USERS)
            .where(Users.USERS.ID.eq(user.id))
            .fetchOne()

        assertThat(found).isNotNull()
        assertThat(found!!.name).isEqualTo("John Doe")
    }

    @Test
    fun `buildList() creates multiple in-memory records`() {
        factory<UsersRecord> {
            name = "User"
            email = sequence { n -> "user${n}@example.com" }
            age = 25
        }

        // Create 10 users at once
        val users = dsl.factory<UsersRecord>().buildList(10)

        assertThat(users).hasSize(10)
        assertThat(users.all { it.id == null }).isTrue()

        // Each user has unique email thanks to sequence
        assertThat(users[0].email).isEqualTo("user1@example.com")
        assertThat(users[9].email).isEqualTo("user10@example.com")
    }

    @Test
    fun `createList() persists multiple records efficiently`() {
        factory<UsersRecord> {
            name = "User"
            email = sequence { n -> "user${n}@example.com" }
            age = 25
        }

        // Create and persist 10 users with batch insert
        val users = dsl.factory<UsersRecord>().createList(10)

        assertThat(users).hasSize(10)
        assertThat(users.all { it.id != null }).isTrue()

        // Verify all records are in database
        val count = dsl.selectCount()
            .from(Users.USERS)
            .fetchOne(0, Int::class.java)

        assertThat(count).isEqualTo(10)
    }

    @Test
    fun `attributes() returns evaluated attributes as a map`() {
        factory<UsersRecord> {
            name = "User"
            email = "user@example.com"
            age = 25
        }

        // Get attributes without creating a record
        val attrs = dsl.factory<UsersRecord>().attributes()

        assertThat(attrs).containsEntry("name", "User")
        assertThat(attrs).containsEntry("email", "user@example.com")
        assertThat(attrs).containsEntry("age", 25)
    }

    @Test
    fun `use attributes() for API request payloads`() {
        factory<UsersRecord> {
            name = "User"
            email = "user@example.com"
            age = 25
        }

        // Generate test data for API requests
        val payload = dsl.factory<UsersRecord>().attributes(mapOf(
            "email" to "custom@example.com"
        ))

        // Use payload in HTTP requests, JSON serialization, etc.
        assertThat(payload["name"]).isEqualTo("User")
        assertThat(payload["email"]).isEqualTo("custom@example.com")
    }

    @Test
    fun `combine factory features in a realistic test scenario`() {
        factory<UsersRecord> {
            name = "Test User"
            email = sequence { n -> "test${n}@example.com" }
            age = 25
        }

        // Create a baseline user
        val user1 = dsl.factory<UsersRecord>().create()

        // Create a user with custom attributes
        val user2 = dsl.factory<UsersRecord>().create(mapOf(
            "name" to "Admin User",
            "age" to 35
        ))

        // Create multiple users for bulk operations
        val bulkUsers = dsl.factory<UsersRecord>().createList(5)

        // Verify everything
        assertThat(user1.name).isEqualTo("Test User")
        assertThat(user2.name).isEqualTo("Admin User")
        assertThat(user2.age).isEqualTo(35)
        assertThat(bulkUsers).hasSize(5)

        val totalUsers = dsl.selectCount()
            .from(Users.USERS)
            .fetchOne(0, Int::class.java)

        assertThat(totalUsers).isEqualTo(7) // 2 individuals + 5 bulk
    }
}
