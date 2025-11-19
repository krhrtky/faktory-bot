package io.github.krhrtky.faktory.dsl

import io.github.krhrtky.faktory.core.DefaultFactoryDefinition
import io.github.krhrtky.faktory.core.StaticAttribute
import io.github.krhrtky.faktory.core.TraitDefinition
import io.github.krhrtky.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TraitCompositionTest {
    @Test
    fun `trait can include another trait`() {
        val definition =
            DefaultFactoryDefinition(
                recordClass = UsersRecord::class,
                attributes = mapOf("name" to StaticAttribute("User")),
                traits =
                    mapOf(
                        "verified" to
                            TraitDefinition(
                                name = "verified",
                                attributes = mapOf("email" to StaticAttribute("verified@example.com")),
                            ),
                        "premium" to
                            TraitDefinition(
                                name = "premium",
                                includedTraits = listOf("verified"),
                                attributes = mapOf("age" to StaticAttribute(30)),
                            ),
                    ),
            )

        val trait = (definition as io.github.krhrtky.faktory.core.DefaultFactoryDefinition).traits["premium"]
        assertThat(trait?.includedTraits).containsExactly("verified")
    }

    @Test
    fun `trait composition applies included traits first`() {
        val baseTrait =
            TraitDefinition<UsersRecord>(
                name = "base",
                attributes = mapOf("name" to StaticAttribute("Base User")),
            )

        val composedTrait =
            TraitDefinition<UsersRecord>(
                name = "composed",
                includedTraits = listOf("base"),
                attributes = mapOf("age" to StaticAttribute(25)),
            )

        val definition =
            DefaultFactoryDefinition(
                recordClass = UsersRecord::class,
                attributes = mapOf("email" to StaticAttribute("user@example.com")),
                traits =
                    mapOf(
                        "base" to baseTrait,
                        "composed" to composedTrait,
                    ),
            )

        val result = composedTrait.applyTo(definition)

        assertThat(result.attributes).containsKey("name")
        assertThat(result.attributes).containsKey("age")
        assertThat(result.attributes).containsKey("email")
    }

    @Test
    fun `trait composition detects circular references`() {
        val trait1 =
            TraitDefinition<UsersRecord>(
                name = "trait1",
                includedTraits = listOf("trait2"),
                attributes = mapOf("field1" to StaticAttribute("value1")),
            )

        val trait2 =
            TraitDefinition<UsersRecord>(
                name = "trait2",
                includedTraits = listOf("trait1"),
                attributes = mapOf("field2" to StaticAttribute("value2")),
            )

        val definition =
            DefaultFactoryDefinition(
                recordClass = UsersRecord::class,
                attributes = emptyMap(),
                traits =
                    mapOf(
                        "trait1" to trait1,
                        "trait2" to trait2,
                    ),
            )

        assertThrows<io.github.krhrtky.faktory.core.CircularTraitReferenceException> {
            trait1.applyTo(definition)
        }
    }

    @Test
    fun `trait can include multiple traits`() {
        val trait1 =
            TraitDefinition<UsersRecord>(
                name = "trait1",
                attributes = mapOf("field1" to StaticAttribute("value1")),
            )

        val trait2 =
            TraitDefinition<UsersRecord>(
                name = "trait2",
                attributes = mapOf("field2" to StaticAttribute("value2")),
            )

        val composedTrait =
            TraitDefinition<UsersRecord>(
                name = "composed",
                includedTraits = listOf("trait1", "trait2"),
                attributes = mapOf("field3" to StaticAttribute("value3")),
            )

        val definition =
            DefaultFactoryDefinition(
                recordClass = UsersRecord::class,
                attributes = emptyMap(),
                traits =
                    mapOf(
                        "trait1" to trait1,
                        "trait2" to trait2,
                        "composed" to composedTrait,
                    ),
            )

        val result = composedTrait.applyTo(definition)

        assertThat(result.attributes).containsKey("field1")
        assertThat(result.attributes).containsKey("field2")
        assertThat(result.attributes).containsKey("field3")
    }

    @Test
    fun `later included trait overrides earlier one`() {
        val trait1 =
            TraitDefinition<UsersRecord>(
                name = "trait1",
                attributes = mapOf("name" to StaticAttribute("First")),
            )

        val trait2 =
            TraitDefinition<UsersRecord>(
                name = "trait2",
                attributes = mapOf("name" to StaticAttribute("Second")),
            )

        val composedTrait =
            TraitDefinition<UsersRecord>(
                name = "composed",
                includedTraits = listOf("trait1", "trait2"),
                attributes = emptyMap(),
            )

        val definition =
            DefaultFactoryDefinition(
                recordClass = UsersRecord::class,
                attributes = emptyMap(),
                traits =
                    mapOf(
                        "trait1" to trait1,
                        "trait2" to trait2,
                        "composed" to composedTrait,
                    ),
            )

        val result = composedTrait.applyTo(definition)
        val nameAttr = result.attributes["name"] as? StaticAttribute<*>
        assertThat(nameAttr?.value).isEqualTo("Second")
    }
}
