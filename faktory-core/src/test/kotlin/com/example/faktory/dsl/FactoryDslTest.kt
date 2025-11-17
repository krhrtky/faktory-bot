package com.example.faktory.dsl

import com.example.faktory.core.DynamicAttribute
import com.example.faktory.core.SequenceAttribute
import com.example.faktory.core.StaticAttribute
import com.example.faktory.test.DynamicTrait
import org.assertj.core.api.Assertions.assertThat
import org.jooq.impl.TableRecordImpl
import org.junit.jupiter.api.Test

class FactoryDslTest {
    @Test
    fun `factory DSL creates definition with static attributes`() {
        val definition =
            factory<TestRecord> {
                attribute("name", "Test User")
                attribute("age", 25)
            }

        assertThat(definition.recordClass).isEqualTo(TestRecord::class)
        assertThat(definition.attributes).containsKey("name")
        assertThat(definition.attributes["name"]).isInstanceOf(StaticAttribute::class.java)
    }

    @Test
    fun `factory DSL supports dynamic attributes`() {
        val definition =
            factory<TestRecord> {
                attribute("random") { "value-${System.currentTimeMillis()}" }
            }

        assertThat(definition.attributes["random"]).isInstanceOf(DynamicAttribute::class.java)
    }

    @Test
    fun `factory DSL supports sequence attributes`() {
        val definition =
            factory<TestRecord> {
                sequenceAttr("email") { n -> "user$n@example.com" }
            }

        assertThat(definition.attributes["email"]).isInstanceOf(SequenceAttribute::class.java)
    }

    @Test
    fun `factory DSL supports named factories`() {
        val definition =
            factory<TestRecord>("admin") {
                attribute("role", "admin")
            }

        assertThat(definition.name).isEqualTo("admin")
    }

    @Test
    fun `factory DSL supports traits`() {
        val premiumTrait = DynamicTrait<TestRecord>("premium")
        val definition =
            factory<TestRecord> {
                attribute("name", "User")

                trait(premiumTrait) {
                    attribute("isPremium", true)
                }
            }

        assertThat((definition as com.example.faktory.core.DefaultFactoryDefinition).traits).containsKey("premium")
    }

    @Test
    fun `factory DSL supports default traits`() {
        val adminTrait = DynamicTrait<TestRecord>("admin")
        val definition =
            factory<TestRecord>(defaultTraits = listOf("admin")) {
                attribute("name", "User")

                trait(adminTrait) {
                    attribute("role", "admin")
                }
            }

        assertThat(definition.defaultTraits).containsExactly("admin")
    }

    @Test
    fun `factory DSL supports multiple default traits`() {
        val adminTrait = DynamicTrait<TestRecord>("admin")
        val verifiedTrait = DynamicTrait<TestRecord>("verified")
        val definition =
            factory<TestRecord>(defaultTraits = listOf("admin", "verified")) {
                attribute("name", "User")

                trait(adminTrait) {
                    attribute("role", "admin")
                }

                trait(verifiedTrait) {
                    attribute("verified", true)
                }
            }

        assertThat(definition.defaultTraits).containsExactly("admin", "verified")
    }
}

class TestRecord : TableRecordImpl<TestRecord>(null)
