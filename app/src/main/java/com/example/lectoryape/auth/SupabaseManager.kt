package com.example.lectoryape.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.Postgrest
import com.example.lectoryape.BuildConfig // <-- Importante

object SupabaseManager {
    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Auth) {
            autoLoadFromStorage = true 
        }
        install(Postgrest)
    }
    val auth: Auth
        get() = client.auth
}