package com.example.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testLoginFlow() {
        composeTestRule.onNodeWithTag("login_email_input").performTextClearance()
        composeTestRule.onNodeWithTag("login_email_input").performTextInput("test@test.com")
        composeTestRule.onNodeWithTag("login_password_input").performTextClearance()
        composeTestRule.onNodeWithTag("login_password_input").performTextInput("123456")
        composeTestRule.onNodeWithTag("login_button").performClick()
        
        composeTestRule.waitForIdle()
    }
}
