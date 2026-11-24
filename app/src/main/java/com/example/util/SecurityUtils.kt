package com.example.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object SecurityUtils {
    private const val PBKDF2_ALGO = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 31000
    private const val KEY_LENGTH_BITS = 256

    fun hashSecret(secret: String): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val derived = deriveKey(secret.toCharArray(), salt, ITERATIONS)
        val saltB64 = Base64.getEncoder().encodeToString(salt)
        val hashB64 = Base64.getEncoder().encodeToString(derived)
        return "pbkdf2:$ITERATIONS:$saltB64:$hashB64"
    }

    fun verifySecret(attempt: String, storedHash: String): Boolean {
        if (!storedHash.startsWith("pbkdf2:")) {
            return false
        }
        val parts = storedHash.split(":")
        if (parts.size != 4) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = runCatching { Base64.getDecoder().decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(parts[3]) }.getOrNull() ?: return false
        val actual = deriveKey(attempt.toCharArray(), salt, iterations)
        return MessageDigest.isEqual(actual, expected)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(PBKDF2_ALGO).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun findActivity(context: Context?): Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }

    fun setScreenCaptureProtection(activity: Activity?, enabled: Boolean) {
        activity ?: return
        if (enabled) {
            activity.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

