package com.messagingapp

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

object SupabaseClient {
    // IMPORTANT: Replace with your actual Supabase anon key
    private const val SUPABASE_URL = "https://ratyoralhhbtqamyjglf.supabase.co"
    private const val SUPABASE_ANON_KEY = "sb_publishable_sW-_hInNqPu4uUEJv8QxTA_83eQHyTX" // Replace this!
    
    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
        install(Storage)
    }
}
