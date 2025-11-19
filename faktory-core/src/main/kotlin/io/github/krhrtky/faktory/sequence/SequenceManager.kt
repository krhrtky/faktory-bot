package io.github.krhrtky.faktory.sequence

interface SequenceManager {
    fun <T> next(
        name: String,
        generator: (Int) -> T,
    ): T

    fun current(name: String): Int

    fun reset(name: String)

    fun resetAll()
}
