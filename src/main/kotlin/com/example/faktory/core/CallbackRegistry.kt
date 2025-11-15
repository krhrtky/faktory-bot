package com.example.faktory.core

import org.jooq.Record

enum class CallbackPhase {
    AFTER_BUILD,
    BEFORE_CREATE,
    AFTER_CREATE,
}

interface CallbackRegistry<T : Record> {
    fun register(
        phase: CallbackPhase,
        callback: (T) -> Unit,
    )

    fun execute(
        phase: CallbackPhase,
        record: T,
    )

    fun merge(other: CallbackRegistry<T>): CallbackRegistry<T>
}

class DefaultCallbackRegistry<T : Record> : CallbackRegistry<T> {
    private val callbacks = mutableMapOf<CallbackPhase, MutableList<(T) -> Unit>>()

    override fun register(
        phase: CallbackPhase,
        callback: (T) -> Unit,
    ) {
        callbacks.computeIfAbsent(phase) { mutableListOf() }.add(callback)
    }

    override fun execute(
        phase: CallbackPhase,
        record: T,
    ) {
        callbacks[phase]?.forEach { it(record) }
    }

    override fun merge(other: CallbackRegistry<T>): CallbackRegistry<T> {
        val merged = DefaultCallbackRegistry<T>()
        callbacks.forEach { (phase, list) ->
            list.forEach { callback ->
                merged.register(phase, callback)
            }
        }
        if (other is DefaultCallbackRegistry) {
            other.callbacks.forEach { (phase, list) ->
                list.forEach { callback ->
                    merged.register(phase, callback)
                }
            }
        }
        return merged
    }
}
