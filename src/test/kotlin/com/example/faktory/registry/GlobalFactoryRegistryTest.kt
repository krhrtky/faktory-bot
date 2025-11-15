package com.example.faktory.registry

import com.example.faktory.core.DefaultFactoryDefinition
import com.example.faktory.core.FactoryNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GlobalFactoryRegistryTest {
    @BeforeEach
    fun setup() {
        GlobalFactoryRegistry.clear()
    }

    @Test
    fun `register and find factory`() {
        val definition =
            DefaultFactoryDefinition(
                recordClass = UserRecord::class,
                name = "user",
            )

        GlobalFactoryRegistry.register(definition)

        val found = GlobalFactoryRegistry.find(UserRecord::class, "user")

        assertThat(found).isEqualTo(definition)
    }

    @Test
    fun `find factory without name`() {
        val definition =
            DefaultFactoryDefinition(
                recordClass = UserRecord::class,
                name = null,
            )

        GlobalFactoryRegistry.register(definition)

        val found = GlobalFactoryRegistry.find(UserRecord::class)

        assertThat(found).isEqualTo(definition)
    }

    @Test
    fun `throw exception when factory not found`() {
        assertThrows<FactoryNotFoundException> {
            GlobalFactoryRegistry.find(UserRecord::class, "undefined")
        }
    }

    @Test
    fun `clear removes all factories`() {
        val definition =
            DefaultFactoryDefinition(
                recordClass = UserRecord::class,
                name = "user",
            )

        GlobalFactoryRegistry.register(definition)
        GlobalFactoryRegistry.clear()

        assertThrows<FactoryNotFoundException> {
            GlobalFactoryRegistry.find(UserRecord::class, "user")
        }
    }

    @Test
    fun `resolve factory returns merged attributes`() {
        val definition =
            DefaultFactoryDefinition(
                recordClass = UserRecord::class,
                name = "user",
            )

        GlobalFactoryRegistry.register(definition)

        val resolved = GlobalFactoryRegistry.resolve(definition)

        assertThat(resolved).isNotNull
        assertThat(resolved.definition).isEqualTo(definition)
    }
}
