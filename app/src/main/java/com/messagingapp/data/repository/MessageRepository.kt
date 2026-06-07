package com.messagingapp.data.repository

import com.messagingapp.SupabaseClient
import com.messagingapp.data.models.*
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.serialization.json.*

class MessageRepository {

    private val client = SupabaseClient.client

    suspend fun getOrCreateConversation(user1Id: String, user2Id: String): Result<String> = runCatching {
        val existing = client.postgrest["conversations"]
            .select {
                filter {
                    or {
                        and {
                            eq("user1_id", user1Id)
                            eq("user2_id", user2Id)
                        }
                        and {
                            eq("user1_id", user2Id)
                            eq("user2_id", user1Id)
                        }
                    }
                }
                limit(1)
            }
            .decodeList<Conversation>()

        if (existing.isNotEmpty()) {
            existing.first().id
        } else {
            val newConv = client.postgrest["conversations"]
                .insert(buildJsonObject {
                    put("user1_id", user1Id)
                    put("user2_id", user2Id)
                }) {
                    select()
                }
                .decodeList<Conversation>()
                .first()
            newConv.id
        }
    }

    suspend fun getConversations(userId: String): Result<List<Conversation>> = runCatching {
        client.postgrest["conversations"]
            .select {
                filter {
                    or {
                        eq("user1_id", userId)
                        eq("user2_id", userId)
                    }
                }
                order("last_message_at", Order.DESCENDING)
            }
            .decodeList<Conversation>()
    }

    suspend fun getMessages(conversationId: String): Result<List<Message>> = runCatching {
        client.postgrest["messages"]
            .select {
                filter {
                    eq("conversation_id", conversationId)
                    isNull("deleted_at")
                }
                order("created_at", Order.ASCENDING)
            }
            .decodeList<Message>()
    }

    suspend fun sendMessage(
        conversationId: String,
        senderId: String,
        content: String,
        replyToId: String? = null,
        replyToContent: String? = null,
        replyToSender: String? = null
    ): Result<Message> = runCatching {
        val body = buildJsonObject {
            put("conversation_id", conversationId)
            put("sender_id", senderId)
            put("content", content)
            put("status", "sent")
            if (replyToId != null) put("reply_to_id", replyToId)
            if (replyToContent != null) put("reply_to_content", replyToContent)
            if (replyToSender != null) put("reply_to_sender", replyToSender)
        }
        val msg = client.postgrest["messages"]
            .insert(body) { select() }
            .decodeList<Message>()
            .first()

        // Update conversation last message
        client.postgrest["conversations"]
            .update(buildJsonObject {
                put("last_message", content)
                put("last_message_at", msg.createdAt)
                put("updated_at", msg.createdAt)
            }) {
                filter { eq("id", conversationId) }
            }
        msg
    }

    suspend fun updateMessageStatus(messageId: String, status: String): Result<Unit> = runCatching {
        client.postgrest["messages"]
            .update(buildJsonObject { put("status", status) }) {
                filter { eq("id", messageId) }
            }
    }

    suspend fun deleteMessage(messageId: String): Result<Unit> = runCatching {
        client.postgrest["messages"]
            .update(buildJsonObject {
                put("deleted_at", kotlinx.datetime.Clock.System.now().toString())
                put("content", "This message was deleted")
            }) {
                filter { eq("id", messageId) }
            }
    }

    suspend fun addReaction(messageId: String, emoji: String, userId: String): Result<Unit> = runCatching {
        val msg = client.postgrest["messages"]
            .select { filter { eq("id", messageId) }; limit(1) }
            .decodeList<Message>()
            .firstOrNull() ?: return@runCatching

        val reactionsMap: MutableMap<String, MutableList<String>> =
            if (msg.reactions != null) {
                val parsed = Json.parseToJsonElement(msg.reactions).jsonObject
                parsed.entries.associate { (k, v) ->
                    k to v.jsonArray.map { it.jsonPrimitive.content }.toMutableList()
                }.toMutableMap()
            } else mutableMapOf()

        val users = reactionsMap.getOrPut(emoji) { mutableListOf() }
        if (userId in users) users.remove(userId) else users.add(userId)
        if (users.isEmpty()) reactionsMap.remove(emoji)

        val newReactions = buildJsonObject {
            reactionsMap.forEach { (k, v) ->
                put(k, buildJsonArray { v.forEach { add(it) } })
            }
        }.toString()

        client.postgrest["messages"]
            .update(buildJsonObject { put("reactions", newReactions) }) {
                filter { eq("id", messageId) }
            }
    }

    suspend fun setTyping(conversationId: String, userId: String, isTyping: Boolean): Result<Unit> = runCatching {
        client.postgrest["typing_status"]
            .upsert(buildJsonObject {
                put("conversation_id", conversationId)
                put("user_id", userId)
                put("is_typing", isTyping)
            })
    }

    suspend fun getPublicUsers(): Result<List<UserProfile>> = runCatching {
        client.postgrest["profiles"]
            .select {
                filter { eq("visibility", "public") }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<UserProfile>()
    }

    suspend fun searchUsers(query: String): Result<List<UserProfile>> = runCatching {
        client.postgrest["profiles"]
            .select {
                filter {
                    eq("visibility", "public")
                    ilike("username", "%$query%")
                }
            }
            .decodeList<UserProfile>()
    }

    fun listenToMessages(conversationId: String): Flow<Message> = callbackFlow {
        val channel = client.realtime.channel("messages:$conversationId")
        val subscription = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
            filter = "conversation_id=eq.$conversationId"
        }
        channel.subscribe()
        subscription.collect { action ->
            runCatching {
                val msg = action.decodeRecord<Message>()
                trySend(msg)
            }
        }
        awaitClose { channel.unsubscribe() }
    }

    fun listenToTyping(conversationId: String): Flow<TypingStatus> = callbackFlow {
        val channel = client.realtime.channel("typing:$conversationId")
        val subscription = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "typing_status"
            filter = "conversation_id=eq.$conversationId"
        }
        channel.subscribe()
        subscription.collect { action ->
            runCatching {
                val typing = action.decodeRecord<TypingStatus>()
                trySend(typing)
            }
        }
        awaitClose { channel.unsubscribe() }
    }

    fun listenToAllMessages(userId: String): Flow<Message> = callbackFlow {
        val channel = client.realtime.channel("all_messages:$userId")
        val subscription = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
        }
        channel.subscribe()
        subscription.collect { action ->
            runCatching {
                val msg = action.decodeRecord<Message>()
                if (msg.senderId != userId) trySend(msg)
            }
        }
        awaitClose { channel.unsubscribe() }
    }
}
