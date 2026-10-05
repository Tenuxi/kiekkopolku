package fi.kiekkopolku.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface CredentialStore {
    suspend fun get(playerId: String): String?
    suspend fun put(playerId: String, code: String)
    suspend fun remove(playerId: String)
}

/** AES-GCM binds the ciphertext to its local profile, with a fresh IV on every write. */
class CredentialCipher(private val key: SecretKey) {
    fun encrypt(profileId: String, plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(profileId.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }
    fun decrypt(profileId: String, encoded: String): String {
        val bytes = Base64.getDecoder().decode(encoded)
        require(bytes.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(profileId.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }
}

class EncryptedCredentialStore(context: Context) : CredentialStore {
    private val preferences = context.getSharedPreferences("metrix_credentials", Context.MODE_PRIVATE)
    private val cipher by lazy {
        val alias = "kiekkopolku.metrix.credentials.v1"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
        CredentialCipher(key)
    }
    override suspend fun get(playerId: String): String? = withContext(Dispatchers.IO) {
        preferences.getString(playerId, null)?.let { cipher.decrypt(playerId, it) }
    }
    override suspend fun put(playerId: String, code: String) = withContext(Dispatchers.IO) {
        check(preferences.edit().putString(playerId, cipher.encrypt(playerId, code)).commit())
    }
    override suspend fun remove(playerId: String) = withContext(Dispatchers.IO) {
        check(preferences.edit().remove(playerId).commit())
    }
}

/** Default for local-only repository construction; production always injects the encrypted store. */
object NoCredentialStore : CredentialStore {
    override suspend fun get(playerId: String): String? = null
    override suspend fun put(playerId: String, code: String): Unit = error("Credential storage not configured")
    override suspend fun remove(playerId: String) = Unit
}
