package com.example.faktory.annotation

@Deprecated(
    message = "KSP-generated factories are deprecated. Use DSL-based factories with type-safe TableField syntax instead: factory<UsersRecord> { USERS.NAME set \"value\" }",
    level = DeprecationLevel.WARNING,
)
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class FactoryGenerate(
    val name: String = "",
)
