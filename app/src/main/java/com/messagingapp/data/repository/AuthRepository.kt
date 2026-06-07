package com.messagingapp.data.repository

import com.messagingapp.SupabaseClient
import com.messagingapp.data.models.UserProfile
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns

class AuthRepository {

    private val client = SupabaseClient.client

    suspend fun signUp(email: String, password: String): Result<Unit> = runCatching {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signOut() {
        runCatching { client.auth.signOut() }
    }

    fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    suspend fun hasProfile(): Boolean {
        val uid = currentUserId() ?: return false
        return runCatching {
            client.postgrest["profiles"]
                .select(Columns.raw("id")) {
                    filter { eq("id", uid) }
                    limit(1)
                }
                .decodeList<UserProfile>()
                .isNotEmpty()
        }.getOrDefault(false)
    }

    suspend fun createProfile(nickname: String, visibility: String): Result<Unit> = runCatching {
        val uid = currentUserId() ?: throw Exception("Not authenticated")
        val randomNumbers = (10000..99999).random()
        val username = "${nickname.lowercase().replace(" ", "_")}$randomNumbers"
        val profile = UserProfile(
            id = uid,
            nickname = nickname,
            username = username,
            visibility = visibility
        )
        client.postgrest["profiles"].upsert(profile)
    }

    suspend fun getProfile(userId: String): Result<UserProfile> = runCatching {
        client.postgrest["profiles"]
            .select {
                filter { eq("id", userId) }
                limit(1)
            }
            .decodeList<UserProfile>()
            .first()
    }
}
