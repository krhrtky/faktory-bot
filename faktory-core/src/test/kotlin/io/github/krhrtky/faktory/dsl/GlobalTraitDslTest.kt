package io.github.krhrtky.faktory.dsl

import io.github.krhrtky.faktory.core.DynamicAttribute
import io.github.krhrtky.faktory.registry.GlobalTraitRegistry
import io.github.krhrtky.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GlobalTraitDslTest {
    @BeforeEach
    fun setup() {
        GlobalTraitRegistry.clear()
    }

    @Test
    fun `globalTrait creates and registers trait`() {
        globalTrait(UsersRecord::class, "timestamps") {
            attribute("created_at", "2024-01-01T00:00:00")
            attribute("updated_at", "2024-01-01T00:00:00")
        }

        val trait = GlobalTraitRegistry.find<UsersRecord>("timestamps")
        assertThat(trait).isNotNull
        assertThat(trait?.name).isEqualTo("timestamps")
        assertThat(trait?.attributes).containsKey("created_at")
        assertThat(trait?.attributes).containsKey("updated_at")
    }

    @Test
    fun `globalTrait supports dynamic attributes`() {
        globalTrait(UsersRecord::class, "dynamic") {
            attribute("value") { "dynamic-value" }
        }

        val trait = GlobalTraitRegistry.find<UsersRecord>("dynamic")
        assertThat(trait?.attributes?.get("value")).isInstanceOf(DynamicAttribute::class.java)
    }

    @Test
    fun `globalTrait supports sequence attributes`() {
        globalTrait(UsersRecord::class, "sequenced") {
            sequenceAttr("email") { n -> "user$n@example.com" }
        }

        val trait = GlobalTraitRegistry.find<UsersRecord>("sequenced")
        assertThat(trait?.attributes?.get("email")).isInstanceOf(
            io.github.krhrtky.faktory.core.SequenceAttribute::class.java,
        )
    }

    @Test
    fun `multiple globalTraits can be defined`() {
        globalTrait(UsersRecord::class, "timestamps") {
            attribute("created_at", "2024-01-01")
        }

        globalTrait(UsersRecord::class, "soft_delete") {
            attribute("deleted_at", null)
        }

        assertThat(GlobalTraitRegistry.find<UsersRecord>("timestamps")).isNotNull
        assertThat(GlobalTraitRegistry.find<UsersRecord>("soft_delete")).isNotNull
    }
}
