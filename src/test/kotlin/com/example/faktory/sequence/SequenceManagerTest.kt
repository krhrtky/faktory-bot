package com.example.faktory.sequence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class SequenceManagerTest {
    private lateinit var sequenceManager: SequenceManager

    @BeforeEach
    fun setup() {
        sequenceManager = DefaultSequenceManager()
    }

    @Test
    fun `next generates sequential values`() {
        val first = sequenceManager.next("user_email") { n -> "user$n@example.com" }
        val second = sequenceManager.next("user_email") { n -> "user$n@example.com" }
        val third = sequenceManager.next("user_email") { n -> "user$n@example.com" }

        assertThat(first).isEqualTo("user1@example.com")
        assertThat(second).isEqualTo("user2@example.com")
        assertThat(third).isEqualTo("user3@example.com")
    }

    @Test
    fun `different sequences are independent`() {
        val email1 = sequenceManager.next("email") { n -> "user$n@example.com" }
        val id1 = sequenceManager.next("id") { n -> n }
        val email2 = sequenceManager.next("email") { n -> "user$n@example.com" }
        val id2 = sequenceManager.next("id") { n -> n }

        assertThat(email1).isEqualTo("user1@example.com")
        assertThat(id1).isEqualTo(1)
        assertThat(email2).isEqualTo("user2@example.com")
        assertThat(id2).isEqualTo(2)
    }

    @Test
    fun `current returns current sequence value`() {
        sequenceManager.next("counter") { it }
        sequenceManager.next("counter") { it }

        val current = sequenceManager.current("counter")

        assertThat(current).isEqualTo(2)
    }

    @Test
    fun `current returns 0 for non-existent sequence`() {
        val current = sequenceManager.current("undefined")

        assertThat(current).isEqualTo(0)
    }

    @Test
    fun `reset resets specific sequence`() {
        sequenceManager.next("counter") { it }
        sequenceManager.next("counter") { it }

        sequenceManager.reset("counter")

        val next = sequenceManager.next("counter") { it }

        assertThat(next).isEqualTo(1)
    }

    @Test
    fun `resetAll resets all sequences`() {
        sequenceManager.next("email") { n -> "user$n@example.com" }
        sequenceManager.next("id") { n -> n }

        sequenceManager.resetAll()

        val email = sequenceManager.next("email") { n -> "user$n@example.com" }
        val id = sequenceManager.next("id") { n -> n }

        assertThat(email).isEqualTo("user1@example.com")
        assertThat(id).isEqualTo(1)
    }
}
