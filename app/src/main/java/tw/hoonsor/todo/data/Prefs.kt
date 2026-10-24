package tw.hoonsor.todo.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 一般設定與同步狀態（SharedPreferences "settings"） */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun cursor(table: String): String? = p.getString("cursor_$table", null)
    fun setCursor(table: String, v: String) = p.edit().putString("cursor_$table", v).apply()

    var lastSyncAt: Long
        get() = p.getLong("lastSyncAt", 0L)
        set(v) = p.edit().putLong("lastSyncAt", v).apply()
    var lastSyncError: String?
        get() = p.getString("lastSyncError", null)
        set(v) = p.edit().putString("lastSyncError", v).apply()

    /** 每個清單的排序方式（manual／due／important／created／alpha） */
    fun sortOf(listKey: String): String = p.getString("sort_$listKey", "due") ?: "due"
    fun setSort(listKey: String, v: String) = p.edit().putString("sort_$listKey", v).apply()

    /** 桌面小工具各自顯示的清單 */
    fun widgetList(widgetId: Int): String = p.getString("widget_$widgetId", "myday") ?: "myday"
    fun setWidgetList(widgetId: Int, key: String) = p.edit().putString("widget_$widgetId", key).apply()

    /** 已排程提醒的待辦 id（重排時先全部取消） */
    var scheduledReminders: Set<String>
        get() = p.getStringSet("scheduledReminders", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("scheduledReminders", v).apply()

    fun clearSync() {
        p.edit().apply {
            p.all.keys.filter { it.startsWith("cursor_") }.forEach { remove(it) }
            remove("lastSyncAt"); remove("lastSyncError")
        }.apply()
    }
}

/** Android Keystore AES-GCM 加密的小型金庫（登入權杖），不進系統備份 */
class KeyVault(context: Context) {
    private val p = context.getSharedPreferences("vault", Context.MODE_PRIVATE)

    fun get(name: String): String? = p.getString(name, null)?.let { decrypt(it) }

    fun put(name: String, value: String?) {
        if (value.isNullOrEmpty()) p.edit().remove(name).apply()
        else p.edit().putString(name, encrypt(value)).apply()
    }

    fun clear() = p.edit().clear().apply()

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String? = runCatching {
        val all = Base64.decode(stored, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        String(c.doFinal(all.copyOfRange(12, all.size)), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val ALIAS = "todo_vault"
    }
}
