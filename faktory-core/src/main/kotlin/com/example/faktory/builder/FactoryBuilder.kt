package com.example.faktory.builder

import com.example.faktory.core.Trait
import org.jooq.Record

interface FactoryBuilder<T : Record> {
    fun build(overrides: Map<String, Any?> = emptyMap()): T

    fun build(vararg traits: Trait<T>): T

    fun create(overrides: Map<String, Any?> = emptyMap()): T

    fun create(vararg traits: Trait<T>): T

    fun buildList(
        count: Int,
        overrides: Map<String, Any?> = emptyMap(),
    ): List<T>

    fun createList(
        count: Int,
        overrides: Map<String, Any?> = emptyMap(),
    ): List<T>

    fun attributes(overrides: Map<String, Any?> = emptyMap()): Map<String, Any?>
}
