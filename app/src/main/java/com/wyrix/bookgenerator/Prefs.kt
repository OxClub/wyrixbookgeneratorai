package com.wyrix.bookgenerator

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** API keys Android Keystore se encrypt hokar store hoti hain. Purani plain keys automatically migrate ho jaati hain. */
object Prefs {
    @Volatile private var cached: SharedPreferences? = null

    fun keys(ctx: Context): SharedPreferences = cached ?: synchronized(this) {
        cached ?: build(ctx.applicationContext).also { cached = it }
    }

    private fun build(app: Context): SharedPreferences {
        val old = app.getSharedPreferences("keys", 0)
        return try {
            val mk = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val enc = EncryptedSharedPreferences.create(
                app, "keys_secure", mk,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            if (old.all.isNotEmpty()) {
                val e = enc.edit()
                old.all.forEach { (k, v) -> if (v is String) e.putString(k, v) }
                e.apply(); old.edit().clear().apply()
            }
            enc
        } catch (e: Exception) { old }
    }
}
