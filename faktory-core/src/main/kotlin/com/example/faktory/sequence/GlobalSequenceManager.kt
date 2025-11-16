package com.example.faktory.sequence

object GlobalSequenceManager {
    private val instance = DefaultSequenceManager()

    fun getInstance(): SequenceManager = instance

    fun reset() {
        instance.resetAll()
    }
}
