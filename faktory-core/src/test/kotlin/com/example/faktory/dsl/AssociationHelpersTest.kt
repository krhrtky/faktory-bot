package com.example.faktory.dsl

import com.example.faktory.core.AssociationAttribute
import com.example.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AssociationHelpersTest {
    @Test
    fun `association creates AssociationAttribute with target class`() {
        val attr = association<UsersRecord>()

        assertThat(attr).isInstanceOf(AssociationAttribute::class.java)
        assertThat(attr.targetClass).isEqualTo(UsersRecord::class)
        assertThat(attr.factoryName).isNull()
        assertThat(attr.overrides).isEmpty()
    }

    @Test
    fun `association with factory name`() {
        val attr = association<UsersRecord>(factoryName = "admin")

        assertThat(attr.factoryName).isEqualTo("admin")
    }

    @Test
    fun `association with overrides`() {
        val attr = association<UsersRecord>(overrides = mapOf("age" to 30))

        assertThat(attr.overrides).containsEntry("age", 30)
    }

    @Test
    fun `association with single trait`() {
        val attr = association<UsersRecord>(traits = listOf("admin"))

        assertThat(attr.traits).containsExactly("admin")
    }

    @Test
    fun `association with multiple traits`() {
        val attr = association<UsersRecord>(traits = listOf("admin", "verified"))

        assertThat(attr.traits).containsExactly("admin", "verified")
    }

    @Test
    fun `association with traits and overrides`() {
        val attr =
            association<UsersRecord>(
                traits = listOf("admin"),
                overrides = mapOf("age" to 30),
            )

        assertThat(attr.traits).containsExactly("admin")
        assertThat(attr.overrides).containsEntry("age", 30)
    }
}
