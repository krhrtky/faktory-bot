package io.github.krhrtky.faktory.registry

import io.github.krhrtky.faktory.core.DefaultFactoryDefinition
import io.github.krhrtky.faktory.core.FactoryNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.jooq.impl.TableRecordImpl
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class FactoryRegistryTest {
    private lateinit var registry: FactoryRegistry

    @BeforeEach
    fun setup() {
        registry = DefaultFactoryRegistry()
    }

    @Test
    fun `register and find factory`() {
        val definition =
            DefaultFactoryDefinition(
                recordClass = UserRecord::class,
                name = "user",
            )

        registry.register(definition)

        val found = registry.find(UserRecord::class, "user")

        assertThat(found).isEqualTo(definition)
    }

    @Test
    fun `find factory without name`() {
        val definition =
            DefaultFactoryDefinition(
                recordClass = UserRecord::class,
                name = null,
            )

        registry.register(definition)

        val found = registry.find(UserRecord::class)

        assertThat(found).isEqualTo(definition)
    }

    @Test
    fun `throw exception when factory not found`() {
        assertThrows<FactoryNotFoundException> {
            registry.find(UserRecord::class, "undefined")
        }
    }

    @Test
    fun `clear removes all factories`() {
        val definition =
            DefaultFactoryDefinition(
                recordClass = UserRecord::class,
                name = "user",
            )

        registry.register(definition)
        registry.clear()

        assertThrows<FactoryNotFoundException> {
            registry.find(UserRecord::class, "user")
        }
    }

    @Test
    fun `resolve factory returns merged attributes`() {
        val definition =
            DefaultFactoryDefinition(
                recordClass = UserRecord::class,
                name = "user",
            )

        registry.register(definition)

        val resolved = registry.resolve(definition)

        assertThat(resolved).isNotNull
        assertThat(resolved.definition).isEqualTo(definition)
    }
}

class UserRecord : TableRecordImpl<UserRecord>(null)
