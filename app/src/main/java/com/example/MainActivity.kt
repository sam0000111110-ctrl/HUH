package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AyerViewModel
import com.example.ui.MainScreen
import com.example.ui.locale.AppLanguage
import com.example.ui.locale.ProvideAppLanguage
import com.example.ui.screens.AgeVerificationGateScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.theme.AyerTheme
import com.example.util.AppPreferencesStore
import com.example.util.authStateFlow
import com.example.util.configureAppCheck
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        val preferencesStore = AppPreferencesStore(applicationContext)
        val firebaseAuth = runCatching { FirebaseAuth.getInstance() }.getOrNull()

        setContent {
            val isDarkPref by preferencesStore.darkThemeFlow.collectAsStateWithLifecycle(initialValue = null)
            val appLanguage by preferencesStore.appLanguageFlow.collectAsStateWithLifecycle(initialValue = AppLanguage.ARABIC)
            val systemInDark = isSystemInDarkTheme()
            val useDark = isDarkPref ?: systemInDark

            AyerTheme(darkTheme = useDark) {
                ProvideAppLanguage(language = appLanguage) {
                    AppRootContent(firebaseAuth = firebaseAuth)
                }
            }
        }
    }
}

@Composable
private fun AppRootContent(firebaseAuth: FirebaseAuth?) {
    val authFlow = remember(firebaseAuth) { firebaseAuth?.authStateFlow() }
    val currentUser by if (authFlow != null) {
        authFlow.collectAsStateWithLifecycle(initialValue = firebaseAuth?.currentUser)
    } else {
        remember { androidx.compose.runtime.mutableStateOf(null) }
    }

    val signedInUser = currentUser
    if (signedInUser == null) {
        LoginScreen(onAuthSuccess = {})
    } else {
        val uid = signedInUser.uid
        key(uid) {
            val viewModel: AyerViewModel = viewModel(key = uid)
            val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
            val ageSubmission by viewModel.ageVerificationSubmission.collectAsStateWithLifecycle()
            val isSubmittingAge by viewModel.isSubmittingAgeVerification.collectAsStateWithLifecycle()
            val ageError by viewModel.ageVerificationError.collectAsStateWithLifecycle()

            if (userProfile?.ageVerified == true) {
                MainScreen(viewModel = viewModel)
            } else {
                AgeVerificationGateScreen(
                    userProfile = userProfile,
                    ageSubmission = ageSubmission,
                    isSubmitting = isSubmittingAge,
                    errorMessage = ageError,
                    onSubmitAgeVerification = { dob, acceptedTerms, acceptedPrivacy ->
                        viewModel.submitAgeVerification(dob, acceptedTerms, acceptedPrivacy)
                    },
                    onSignOut = { viewModel.logoutUser() }
                )
            }
        }
    }
}
