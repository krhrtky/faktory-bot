package com.example.faktory.integration

import com.example.faktory.association.CircularDependencyDetector
import com.example.faktory.association.DefaultAssociationResolver
import com.example.faktory.builder.DefaultFactoryBuilder
import com.example.faktory.core.DefaultFactoryDefinition
import com.example.faktory.core.StaticAttribute
import com.example.faktory.core.TraitDefinition
import com.example.faktory.dsl.association
import com.example.faktory.registry.DefaultFactoryRegistry
import com.example.faktory.sequence.DefaultSequenceManager
import com.example.faktory.test.JooqTestBase
import com.example.faktory.test.jooq.tables.Posts
import com.example.faktory.test.jooq.tables.Users
import com.example.faktory.test.jooq.tables.records.PostsRecord
import com.example.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class AssociationIntegrationTest : JooqTestBase() {
    private lateinit var factoryRegistry: DefaultFactoryRegistry
    private lateinit var sequenceManager: DefaultSequenceManager
    private lateinit var associationResolver: DefaultAssociationResolver

    @BeforeEach
    fun setup() {
        factoryRegistry = DefaultFactoryRegistry()
        sequenceManager = DefaultSequenceManager()
        associationResolver =
            DefaultAssociationResolver(
                dsl = dsl,
                factoryRegistry = factoryRegistry,
                circularDependencyDetector = CircularDependencyDetector(),
            )
    }

    @Test
    fun `create post with association to user`() {
        val userFactory =
            DefaultFactoryDefinition(
                recordClass = UsersRecord::class,
                attributes =
                    mapOf(
                        "name" to StaticAttribute("Test User"),
                        "email" to StaticAttribute("user@example.com"),
                        "age" to StaticAttribute(25),
                        "created_at" to StaticAttribute(LocalDateTime.now()),
                        "updated_at" to StaticAttribute(LocalDateTime.now()),
                    ),
            )
        factoryRegistry.register(userFactory)

        val postFactory =
            DefaultFactoryDefinition(
                recordClass = PostsRecord::class,
                attributes =
                    mapOf(
                        "user_id" to association<UsersRecord>(),
                        "title" to StaticAttribute("Test Post"),
                        "content" to StaticAttribute("Test Content"),
                        "published" to StaticAttribute(false),
                        "created_at" to StaticAttribute(LocalDateTime.now()),
                        "updated_at" to StaticAttribute(LocalDateTime.now()),
                    ),
            )

        val builder = DefaultFactoryBuilder(dsl, postFactory, sequenceManager, associationResolver)
        val post = builder.create()

        assertThat(post.userId).isNotNull
        assertThat(post.title).isEqualTo("Test Post")

        val users = dsl.selectFrom(Users.USERS).fetch()
        assertThat(users).hasSize(1)
        assertThat(users[0].id).isEqualTo(post.userId)
        assertThat(users[0].name).isEqualTo("Test User")

        val posts = dsl.selectFrom(Posts.POSTS).fetch()
        assertThat(posts).hasSize(1)
    }

    @Test
    fun `build post with association to user (in-memory only)`() {
        val userFactory =
            DefaultFactoryDefinition(
                recordClass = UsersRecord::class,
                attributes =
                    mapOf(
                        "name" to StaticAttribute("Test User"),
                        "email" to StaticAttribute("user@example.com"),
                        "age" to StaticAttribute(25),
                        "created_at" to StaticAttribute(LocalDateTime.now()),
                        "updated_at" to StaticAttribute(LocalDateTime.now()),
                    ),
            )
        factoryRegistry.register(userFactory)

        val postFactory =
            DefaultFactoryDefinition(
                recordClass = PostsRecord::class,
                attributes =
                    mapOf(
                        "user_id" to association<UsersRecord>(),
                        "title" to StaticAttribute("Test Post"),
                        "content" to StaticAttribute("Test Content"),
                        "published" to StaticAttribute(false),
                        "created_at" to StaticAttribute(LocalDateTime.now()),
                        "updated_at" to StaticAttribute(LocalDateTime.now()),
                    ),
            )

        val builder = DefaultFactoryBuilder(dsl, postFactory, sequenceManager, associationResolver)
        val post = builder.build()

        assertThat(post.userId).isNull()
        assertThat(post.title).isEqualTo("Test Post")

        val users = dsl.selectFrom(Users.USERS).fetch()
        assertThat(users).isEmpty()
    }

    @Test
    fun `create post with association to user with trait`() {
        val userFactory =
            DefaultFactoryDefinition(
                recordClass = UsersRecord::class,
                attributes =
                    mapOf(
                        "name" to StaticAttribute("Regular User"),
                        "email" to StaticAttribute("user@example.com"),
                        "age" to StaticAttribute(25),
                        "created_at" to StaticAttribute(LocalDateTime.now()),
                        "updated_at" to StaticAttribute(LocalDateTime.now()),
                    ),
                traits =
                    mapOf(
                        "admin" to
                            TraitDefinition(
                                name = "admin",
                                attributes =
                                    mapOf(
                                        "name" to StaticAttribute("Admin User"),
                                        "age" to StaticAttribute(35),
                                    ),
                            ),
                    ),
            )
        factoryRegistry.register(userFactory)

        val postFactory =
            DefaultFactoryDefinition(
                recordClass = PostsRecord::class,
                attributes =
                    mapOf(
                        "user_id" to association<UsersRecord>(traits = listOf("admin")),
                        "title" to StaticAttribute("Test Post"),
                        "content" to StaticAttribute("Test Content"),
                        "published" to StaticAttribute(false),
                        "created_at" to StaticAttribute(LocalDateTime.now()),
                        "updated_at" to StaticAttribute(LocalDateTime.now()),
                    ),
            )

        val builder = DefaultFactoryBuilder(dsl, postFactory, sequenceManager, associationResolver)
        val post = builder.create()

        assertThat(post.userId).isNotNull

        val users = dsl.selectFrom(Users.USERS).fetch()
        assertThat(users).hasSize(1)
        assertThat(users[0].id).isEqualTo(post.userId)
        assertThat(users[0].name).isEqualTo("Admin User")
        assertThat(users[0].age).isEqualTo(35)
    }

    @Test
    fun `create post with association to user with multiple traits`() {
        val userFactory =
            DefaultFactoryDefinition(
                recordClass = UsersRecord::class,
                attributes =
                    mapOf(
                        "name" to StaticAttribute("Regular User"),
                        "email" to StaticAttribute("user@example.com"),
                        "age" to StaticAttribute(25),
                        "created_at" to StaticAttribute(LocalDateTime.now()),
                        "updated_at" to StaticAttribute(LocalDateTime.now()),
                    ),
                traits =
                    mapOf(
                        "admin" to
                            TraitDefinition(
                                name = "admin",
                                attributes =
                                    mapOf(
                                        "name" to StaticAttribute("Admin User"),
                                    ),
                            ),
                        "senior" to
                            TraitDefinition(
                                name = "senior",
                                attributes =
                                    mapOf(
                                        "age" to StaticAttribute(65),
                                    ),
                            ),
                    ),
            )
        factoryRegistry.register(userFactory)

        val postFactory =
            DefaultFactoryDefinition(
                recordClass = PostsRecord::class,
                attributes =
                    mapOf(
                        "user_id" to association<UsersRecord>(traits = listOf("admin", "senior")),
                        "title" to StaticAttribute("Test Post"),
                        "content" to StaticAttribute("Test Content"),
                        "published" to StaticAttribute(false),
                        "created_at" to StaticAttribute(LocalDateTime.now()),
                        "updated_at" to StaticAttribute(LocalDateTime.now()),
                    ),
            )

        val builder = DefaultFactoryBuilder(dsl, postFactory, sequenceManager, associationResolver)
        val post = builder.create()

        assertThat(post.userId).isNotNull

        val users = dsl.selectFrom(Users.USERS).fetch()
        assertThat(users).hasSize(1)
        assertThat(users[0].id).isEqualTo(post.userId)
        assertThat(users[0].name).isEqualTo("Admin User")
        assertThat(users[0].age).isEqualTo(65)
    }
}
