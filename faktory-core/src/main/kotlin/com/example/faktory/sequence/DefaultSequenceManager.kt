package com.example.faktory.sequence

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class DefaultSequenceManager : SequenceManager {
    private val sequences = ConcurrentHashMap<String, AtomicInteger>()

    override fun <T> next(
        name: String,
        generator: (Int) -> T,
    ): T {
        val counter = sequences.computeIfAbsent(name) { AtomicInteger(0) }
        val value = counter.incrementAndGet()
        return generator(value)
    }

    override fun current(name: String): Int = sequences[name]?.get() ?: 0

    override fun reset(name: String) {
        sequences[name]?.set(0)
    }

    override fun resetAll() {
        sequences.clear()
    }
}
