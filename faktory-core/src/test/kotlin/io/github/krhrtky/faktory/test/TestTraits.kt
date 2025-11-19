package io.github.krhrtky.faktory.test

import io.github.krhrtky.faktory.core.Trait
import io.github.krhrtky.faktory.test.jooq.tables.records.UsersRecord
import org.jooq.Record

sealed class CommonUserTrait : Trait<UsersRecord> {
    data object Admin : CommonUserTrait() {
        override val name = "admin"
    }

    data object Verified : CommonUserTrait() {
        override val name = "verified"
    }

    data object Premium : CommonUserTrait() {
        override val name = "premium"
    }

    data object Senior : CommonUserTrait() {
        override val name = "senior"
    }

    data object Young : CommonUserTrait() {
        override val name = "young"
    }

    data object Old : CommonUserTrait() {
        override val name = "old"
    }

    data object WithCallback : CommonUserTrait() {
        override val name = "withCallback"
    }

    data object WithTransient : CommonUserTrait() {
        override val name = "withTransient"
    }

    data object WithPosts : CommonUserTrait() {
        override val name = "withPosts"
    }

    data object WithManyPosts : CommonUserTrait() {
        override val name = "withManyPosts"
    }

    data object T1 : CommonUserTrait() {
        override val name = "t1"
    }

    data object T2 : CommonUserTrait() {
        override val name = "t2"
    }
}

class DynamicTrait<T : Record>(override val name: String) : Trait<T>
