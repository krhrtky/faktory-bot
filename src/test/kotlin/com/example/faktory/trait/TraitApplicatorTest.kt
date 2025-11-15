package com.example.faktory.trait

import com.example.faktory.core.*
import com.example.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TraitApplicatorTest {
    @Test
    fun `apply single trait overrides attributes`() {
        val baseDef =
            DefaultFactoryDefinition(
                recordClass = UsersRecord::class,
                attributes =
                    mapOf(
                        "role" to StaticAttribute("MEMBER"),
                    ),
                traits =
                    mapOf(
                        "admin" to
                            TraitDefinition(
                                name = "admin",
                                attributes =
                                    mapOf(
                                        "role" to StaticAttribute("ADMIN"),
                                    ),
                            ),
                    ),
            )

        val applicator = TraitApplicator<UsersRecord>()
        val result = applicator.apply(baseDef, listOf("admin"))

        assertThat(result.attributes["role"]).isInstanceOf(StaticAttribute::class.java)
        val roleAttr = result.attributes["role"] as StaticAttribute<*>
        assertThat(roleAttr.value).isEqualTo("ADMIN")
    }

    @Test
    fun `apply multiple traits in order`() {
        val baseDef =
            DefaultFactoryDefinition(
                recordClass = UsersRecord::class,
                attributes =
                    mapOf(
                        "name" to StaticAttribute("User"),
                    ),
                traits =
                    mapOf(
                        "verified" to
                            TraitDefinition(
                                name = "verified",
                                attributes =
                                    mapOf(
                                        "verified" to StaticAttribute(true),
                                    ),
                            ),
                        "admin" to
                            TraitDefinition(
                                name = "admin",
                                attributes =
                                    mapOf(
                                        "role" to StaticAttribute("ADMIN"),
                                    ),
                            ),
                    ),
            )

        val applicator = TraitApplicator<UsersRecord>()
        val result = applicator.apply(baseDef, listOf("verified", "admin"))

        assertThat(result.attributes["verified"]).isInstanceOf(StaticAttribute::class.java)
        assertThat(result.attributes["role"]).isInstanceOf(StaticAttribute::class.java)
    }

    @Test
    fun `throws exception for non-existent trait`() {
        val baseDef =
            DefaultFactoryDefinition<UsersRecord>(
                recordClass = UsersRecord::class,
            )

        val applicator = TraitApplicator<UsersRecord>()

        assertThatThrownBy {
            applicator.apply(baseDef, listOf("nonexistent"))
        }.isInstanceOf(TraitNotFoundException::class.java)
            .hasMessageContaining("nonexistent")
    }

    @Test
    fun `apply trait with callbacks`() {
        var callbackExecuted = false

        val callbacks = DefaultCallbackRegistry<UsersRecord>()
        callbacks.register(CallbackPhase.AFTER_CREATE) { _, _ ->
            callbackExecuted = true
        }

        val baseDef =
            DefaultFactoryDefinition(
                recordClass = UsersRecord::class,
                traits =
                    mapOf(
                        "withCallback" to
                            TraitDefinition(
                                name = "withCallback",
                                callbacks = callbacks,
                            ),
                    ),
            )

        val applicator = TraitApplicator<UsersRecord>()
        val result = applicator.apply(baseDef, listOf("withCallback"))

        result.callbacks.execute(CallbackPhase.AFTER_CREATE, UsersRecord(), TransientContext())

        assertThat(callbackExecuted).isTrue()
    }

    @Test
    fun `apply trait with transients`() {
        val baseDef =
            DefaultFactoryDefinition(
                recordClass = UsersRecord::class,
                traits =
                    mapOf(
                        "withTransient" to
                            TraitDefinition(
                                name = "withTransient",
                                transients = TransientDefinition(mapOf("key" to "value")),
                            ),
                    ),
            )

        val applicator = TraitApplicator<UsersRecord>()
        val result = applicator.apply(baseDef, listOf("withTransient"))

        assertThat(result.transients.properties["key"]).isEqualTo("value")
    }
}
