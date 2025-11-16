package com.example.faktory.registry

import com.example.faktory.core.StaticAttribute
import com.example.faktory.core.TraitDefinition
import com.example.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GlobalTraitRegistryTest {
    @BeforeEach
    fun setup() {
        GlobalTraitRegistry.clear()
    }

    @Test
    fun `register and find global trait`() {
        val trait =
            TraitDefinition<UsersRecord>(
                name = "timestamps",
                attributes =
                    mapOf(
                        "created_at" to StaticAttribute("2024-01-01T00:00:00"),
                    ),
            )

        GlobalTraitRegistry.register("timestamps", trait)

        val found = GlobalTraitRegistry.find<UsersRecord>("timestamps")
        assertThat(found).isNotNull
        assertThat(found?.name).isEqualTo("timestamps")
    }

    @Test
    fun `find returns null for non-existent trait`() {
        val found = GlobalTraitRegistry.find<UsersRecord>("nonexistent")
        assertThat(found).isNull()
    }

    @Test
    fun `clear removes all traits`() {
        val trait =
            TraitDefinition<UsersRecord>(
                name = "timestamps",
                attributes = mapOf("created_at" to StaticAttribute("2024-01-01T00:00:00")),
            )

        GlobalTraitRegistry.register("timestamps", trait)
        assertThat(GlobalTraitRegistry.find<UsersRecord>("timestamps")).isNotNull

        GlobalTraitRegistry.clear()
        assertThat(GlobalTraitRegistry.find<UsersRecord>("timestamps")).isNull()
    }

    @Test
    fun `can register multiple traits`() {
        val trait1 =
            TraitDefinition<UsersRecord>(
                name = "timestamps",
                attributes = mapOf("created_at" to StaticAttribute("2024-01-01T00:00:00")),
            )
        val trait2 =
            TraitDefinition<UsersRecord>(
                name = "soft_delete",
                attributes = mapOf("deleted_at" to StaticAttribute(null)),
            )

        GlobalTraitRegistry.register("timestamps", trait1)
        GlobalTraitRegistry.register("soft_delete", trait2)

        assertThat(GlobalTraitRegistry.find<UsersRecord>("timestamps")).isNotNull
        assertThat(GlobalTraitRegistry.find<UsersRecord>("soft_delete")).isNotNull
    }

    @Test
    fun `later registration overwrites earlier one`() {
        val trait1 =
            TraitDefinition<UsersRecord>(
                name = "test",
                attributes = mapOf("value" to StaticAttribute("first")),
            )
        val trait2 =
            TraitDefinition<UsersRecord>(
                name = "test",
                attributes = mapOf("value" to StaticAttribute("second")),
            )

        GlobalTraitRegistry.register("test", trait1)
        GlobalTraitRegistry.register("test", trait2)

        val found = GlobalTraitRegistry.find<UsersRecord>("test")
        assertThat(found?.attributes?.get("value")).isInstanceOf(StaticAttribute::class.java)
        val staticAttr = found?.attributes?.get("value") as? StaticAttribute<*>
        assertThat(staticAttr?.value).isEqualTo("second")
    }
}
