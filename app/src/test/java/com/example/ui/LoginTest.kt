package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.ui.screens.LoginScreen
import com.example.ui.theme.AyerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoginTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loginScreenRendersGoogleSignInAndLegalPolicies() {
        composeTestRule.setContent {
            AyerTheme {
                LoginScreen(onAuthSuccess = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("privacy_policy_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("terms_of_service_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("privacy_policy_button").performClick()
        composeTestRule.waitForIdle()
    }
}

