package com.example.faktory.examples

import com.example.faktory.core.Trait
import com.example.faktory.examples.jooq.tables.records.PostsRecord
import com.example.faktory.examples.jooq.tables.records.UsersRecord

sealed class UserTrait : Trait<UsersRecord> {
    data object Admin : UserTrait() {
        override val name = "admin"
    }

    data object Premium : UserTrait() {
        override val name = "premium"
    }

    data object Verified : UserTrait() {
        override val name = "verified"
    }

    data object Senior : UserTrait() {
        override val name = "senior"
    }

    data object Young : UserTrait() {
        override val name = "young"
    }

    data object Old : UserTrait() {
        override val name = "old"
    }
}

sealed class PostTrait : Trait<PostsRecord> {
    data object Published : PostTrait() {
        override val name = "published"
    }

    data object Draft : PostTrait() {
        override val name = "draft"
    }

    data object Featured : PostTrait() {
        override val name = "featured"
    }

    data object Long : PostTrait() {
        override val name = "long"
    }

    data object Short : PostTrait() {
        override val name = "short"
    }
}
