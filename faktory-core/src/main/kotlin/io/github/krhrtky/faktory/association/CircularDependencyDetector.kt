package io.github.krhrtky.faktory.association

import io.github.krhrtky.faktory.core.CircularAssociationException
import kotlin.reflect.KClass

class CircularDependencyDetector {
    private val stack = ThreadLocal.withInitial { mutableListOf<KClass<*>>() }

    fun <T> withCheck(
        recordClass: KClass<*>,
        block: () -> T,
    ): T {
        val currentStack = stack.get()

        if (recordClass in currentStack) {
            throw CircularAssociationException(currentStack + recordClass)
        }

        currentStack.add(recordClass)
        try {
            return block()
        } finally {
            currentStack.removeLast()
        }
    }

    fun reset() {
        stack.get().clear()
    }
}
