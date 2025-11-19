package io.github.krhrtky.faktory.annotation

@RequiresOptIn(
    message =
        "This is an internal API that should not be used directly. " +
            "Use the factory DSL instead: factory<T> { USERS.NAME set \"value\" }",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
)
annotation class InternalFactoryApi
