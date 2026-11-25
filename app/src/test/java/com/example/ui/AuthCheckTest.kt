package com.example.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.auth.FirebaseAuth
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AuthCheckTest {
    @Test
    fun testAuth() {
        try {
            val auth = FirebaseAuth.getInstance()
            println("AUTH_IS_NOT_NULL")
        } catch (e: Exception) {
            println("AUTH_IS_NULL_EXCEPTION: " + e.message)
        }
    }
}
