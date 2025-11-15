package com.example.faktory.examples

import com.example.faktory.dsl.factory
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import org.jooq.DSLContext
import org.jooq.Record
import java.time.LocalDateTime
import java.util.UUID

class AdvancedExample(private val dsl: DSLContext) {
    init {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    fun defineFactoryWithCustomSequence() {
        factory<EventRecord> {
            name = "Event"

            attribute("scheduledAt") { context ->
                val n = context.sequenceManager.next("event_date")
                LocalDateTime.now().plusDays(n.toLong())
            }
        }
    }

    fun defineFactoryWithUuidSequence() {
        factory<ApiKeyRecord> {
            name = "API Key"

            attribute("key") { context ->
                val n = context.sequenceManager.next("api_key")
                UUID.nameUUIDFromBytes("api_key_$n".toByteArray()).toString()
            }
        }
    }

    fun defineFactoryWithEnumSequence() {
        enum class Status { PENDING, ACTIVE, COMPLETED, CANCELLED }

        factory<OrderRecord> {
            total = 100

            attribute("status") { context ->
                val n = context.sequenceManager.next("order_status")
                val statuses = Status.values()
                statuses[n % statuses.size].name
            }
        }
    }

    fun createComplexTestData() {
        val author = dsl.factory<UserRecord>().create(mapOf(
            "name" to "Author"
        ))

        val post = dsl.factory<PostRecord>().create(mapOf(
            "userId" to author.id,
            "title" to "My Post"
        ))

        val commenters = dsl.factory<UserRecord>().createList(3)
        val comments = commenters.map { commenter ->
            dsl.factory<CommentRecord>().create(mapOf(
                "postId" to post.id,
                "userId" to commenter.id,
                "content" to "Great post!"
            ))
        }

        val tags = dsl.factory<TagRecord>().createList(5)

        println("Created:")
        println("  - 1 author")
        println("  - 1 post")
        println("  - ${commenters.size} commenters")
        println("  - ${comments.size} comments")
        println("  - ${tags.size} tags")
    }

    fun defineFactoryComposition() {
        class TestDataFactory(private val dsl: DSLContext) {
            fun createBlogPost(
                authorName: String = "Author",
                commentsCount: Int = 0,
                tags: List<String> = emptyList()
            ): PostRecord {
                val author = dsl.factory<UserRecord>().create(mapOf(
                    "name" to authorName
                ))

                val post = dsl.factory<PostRecord>().create(mapOf(
                    "userId" to author.id
                ))

                repeat(commentsCount) {
                    dsl.factory<CommentRecord>().create(mapOf(
                        "postId" to post.id,
                        "userId" to author.id
                    ))
                }

                tags.forEach { tagName ->
                    val tag = dsl.factory<TagRecord>().create(mapOf(
                        "name" to tagName
                    ))
                }

                return post
            }
        }

        val factory = TestDataFactory(dsl)
        val post = factory.createBlogPost(
            authorName = "John Doe",
            commentsCount = 5,
            tags = listOf("kotlin", "testing", "jooq")
        )

        println("Created blog post with author, comments, and tags")
    }

    fun demonstratePerformanceOptimization() {
        println("Performance Comparison:")

        val start1 = System.currentTimeMillis()
        repeat(100) {
            dsl.factory<UserRecord>().create()
        }
        val time1 = System.currentTimeMillis() - start1
        println("  100 individual creates: ${time1}ms")

        GlobalSequenceManager.reset()

        val start2 = System.currentTimeMillis()
        dsl.factory<UserRecord>().createList(100)
        val time2 = System.currentTimeMillis() - start2
        println("  createList(100): ${time2}ms")

        val speedup = time1.toDouble() / time2.toDouble()
        println("  Speedup: ${String.format("%.1f", speedup)}x")
    }

    fun demonstrateBatchWithCallbackControl() {
        factory<UserRecord> {
            name = "User"

            transient {
                set("skipCallbacks", false)
            }

            afterCreate { user, transients ->
                val skip = transients.getOrNull("skipCallbacks") as? Boolean ?: false
                if (!skip) {
                    println("Expensive operation for user ${user.id}")
                }
            }
        }

        val users = dsl.factory<UserRecord>().createList(1000, mapOf(
            "skipCallbacks" to true
        ))

        println("Created ${users.size} users with callbacks disabled")
    }

    fun demonstrateOneToManyAssociation() {
        factory<UserRecord> {
            name = "User"

            afterCreate { user, _ ->
                dsl.factory<PostRecord>().createList(5, mapOf(
                    "userId" to user.id
                ))
            }
        }

        val user = dsl.factory<UserRecord>().create()
        println("User created with 5 posts")
    }

    fun demonstrateManyToManyAssociation() {
        factory<UserRecord> {
            name = "User"

            transient {
                set("groupIds", emptyList<Long>())
            }

            afterCreate { user, transients ->
                @Suppress("UNCHECKED_CAST")
                val groupIds = transients.getOrNull("groupIds") as? List<Long> ?: emptyList()
                groupIds.forEach { groupId ->
                    println("Linking user ${user.id} to group $groupId")
                }
            }
        }

        val group1 = dsl.factory<GroupRecord>().create()
        val group2 = dsl.factory<GroupRecord>().create()

        val user = dsl.factory<UserRecord>().create(mapOf(
            "groupIds" to listOf(group1.id!!, group2.id!!)
        ))

        println("User linked to ${listOf(group1.id, group2.id).size} groups")
    }

    fun demonstratePolymorphicAssociation() {
        factory<CommentRecord> {
            content = "Comment"

            transient {
                set("commentableType", "Post")
                set("commentableId", null)
            }

            beforeCreate { comment, transients ->
                val type = transients.getOrNull("commentableType") as? String
                val id = transients.getOrNull("commentableId") as? Long

                comment.commentableType = type
                comment.commentableId = id
            }
        }

        val post = dsl.factory<PostRecord>().create()

        val comment = dsl.factory<CommentRecord>().create(mapOf(
            "commentableType" to "Post",
            "commentableId" to post.id
        ))

        println("Comment on post ${post.id}")
    }
}

interface UserRecord : Record {
    var id: Long?
    var name: String
    var email: String
}

interface PostRecord : Record {
    var id: Long?
    var userId: Long
    var title: String
    var content: String
}

interface CommentRecord : Record {
    var id: Long?
    var postId: Long?
    var userId: Long
    var content: String
    var commentableType: String?
    var commentableId: Long?
}

interface TagRecord : Record {
    var id: Long?
    var name: String
}

interface EventRecord : Record {
    var id: Long?
    var name: String
    var scheduledAt: LocalDateTime
}

interface ApiKeyRecord : Record {
    var id: Long?
    var name: String
    var key: String
}

interface OrderRecord : Record {
    var id: Long?
    var status: String
    var total: Int
}

interface GroupRecord : Record {
    var id: Long?
    var name: String
}

fun main() {
    println("Advanced Example")
    println("================")
    println()
    println("Complex Patterns:")
    println("1. Custom Sequences")
    println("   - Date sequences: LocalDateTime.now().plusDays(n)")
    println("   - UUID sequences: UUID.nameUUIDFromBytes(...)")
    println("   - Enum sequences: values()[n % values().size]")
    println()
    println("2. Associations")
    println("   - One-to-Many: Create related records in afterCreate")
    println("   - Many-to-Many: Link via join table in afterCreate")
    println("   - Polymorphic: Set type and ID fields")
    println()
    println("3. Performance Optimization")
    println("   - Use createList() for bulk inserts (20-30x faster)")
    println("   - Disable callbacks for large batches")
    println("   - Lazy load associations as needed")
    println()
    println("4. Factory Composition")
    println("   - Build wrapper classes for complex scenarios")
    println("   - Compose multiple factories for test setup")
    println("   - Reuse patterns across tests")
    println()
    println("5. Multi-Factory Scenarios")
    println("   - Blog post with author, comments, and tags")
    println("   - E-commerce order with items and payments")
    println("   - Social network with users, posts, and relationships")
    println()
    println("Best Practices:")
    println("- Keep factories simple")
    println("- Use traits for variations")
    println("- Batch operations for performance")
    println("- Document complex patterns")
    println("- Test factory behavior")
    println()
    println("See AdvancedExampleTest.kt for working test examples")
}
