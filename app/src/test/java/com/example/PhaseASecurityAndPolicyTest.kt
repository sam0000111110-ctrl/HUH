package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.example.data.model.UserProfile
import com.example.data.remote.FeatureFlagsDto
import com.example.ui.screens.AgeVerificationGateScreen
import com.example.ui.theme.AyerTheme
import com.example.util.AgePolicyUtils
import com.example.util.QrMatrixGenerator
import com.example.util.SecurityUtils
import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhaseASecurityAndPolicyTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `SecurityUtils hashes with PBKDF2 salt and rejects plaintext or wrong secrets`() {
        val secret = "Passw0rd!2026"
        val hash1 = SecurityUtils.hashSecret(secret)
        val hash2 = SecurityUtils.hashSecret(secret)

        assertTrue(hash1.startsWith("pbkdf2:"))
        assertNotEquals(hash1, hash2)
        assertTrue(SecurityUtils.verifySecret(secret, hash1))
        assertTrue(SecurityUtils.verifySecret(secret, hash2))
        assertFalse(SecurityUtils.verifySecret("wrong", hash1))
        assertFalse(SecurityUtils.verifySecret(secret, secret))
    }

    @Test
    fun `AgePolicyUtils validates 18+ boundary, rejects minors and normalizes Arabic numerals`() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            set(2026, Calendar.NOVEMBER, 23, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val fixedNow = cal.timeInMillis

        // Exactly 18 years old on 2026-11-23
        val exactAdult = AgePolicyUtils.evaluateDob("2008-11-23", fixedNow)
        assertTrue(exactAdult.isValidFormat)
        assertTrue(exactAdult.isAdult)
        assertEquals(18, exactAdult.ageYears)

        // Turns 18 tomorrow -> still 17
        val minorByOneDay = AgePolicyUtils.evaluateDob("2008-11-24", fixedNow)
        assertTrue(minorByOneDay.isValidFormat)
        assertFalse(minorByOneDay.isAdult)
        assertEquals(17, minorByOneDay.ageYears)

        // Arabic/Persian digits normalization
        val arabicDigits = AgePolicyUtils.evaluateDob("١٩٩٨-٠٥-١٤", fixedNow)
        assertTrue(arabicDigits.isValidFormat)
        assertTrue(arabicDigits.isAdult)
        assertEquals("1998-05-14", arabicDigits.normalizedDob)

        // Invalid day / future date
        assertFalse(AgePolicyUtils.evaluateDob("2000-02-30", fixedNow).isValidFormat)
        assertFalse(AgePolicyUtils.evaluateDob("2030-01-01", fixedNow).isValidFormat)
    }

    @Test
    fun `UserProfile defaults to zero privileges, zero free points, and unverified age`() {
        val profile = UserProfile()
        assertEquals(0, profile.pointsBalance)
        assertFalse(profile.isAgent)
        assertFalse(profile.isAdmin)
        assertFalse(profile.isHost)
        assertFalse(profile.ageVerified)
    }

    @Test
    fun `FeatureFlagsDto defaults all unhardened financial features to false`() {
        val flags = FeatureFlagsDto()
        assertFalse(flags.payoutsEnabled)
        assertFalse(flags.missionRewardsEnabled)
        assertFalse(flags.paidMediaEnabled)
        assertFalse(flags.agenciesEnabled)
    }

    @Test
    fun `QrMatrixGenerator produces genuine ISO-18004 QR matrix decodable by ZXing QRCodeReader`() {
        val targetUri = "https://ayer.app/u/42"
        val matrix = QrMatrixGenerator.encode(targetUri)
        assertTrue(matrix.size >= 21)

        val scale = 4
        val borderModules = 2
        val totalModules = matrix.size + borderModules * 2
        val dim = totalModules * scale
        val luminance = ByteArray(dim * dim) { 0xFF.toByte() }
        for (r in 0 until matrix.size) {
            for (c in 0 until matrix.size) {
                if (matrix.get(r, c)) {
                    val startY = (r + borderModules) * scale
                    val startX = (c + borderModules) * scale
                    for (dy in 0 until scale) {
                        for (dx in 0 until scale) {
                            luminance[(startY + dy) * dim + (startX + dx)] = 0x00.toByte()
                        }
                    }
                }
            }
        }

        val source = object : LuminanceSource(dim, dim) {
            override fun getRow(y: Int, row: ByteArray?): ByteArray {
                val out = if (row != null && row.size >= dim) row else ByteArray(dim)
                System.arraycopy(luminance, y * dim, out, 0, dim)
                return out
            }

            override fun getMatrix(): ByteArray = luminance
        }

        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)))
        assertEquals(targetUri, decoded.text)
    }

    @Test
    fun `AgeVerificationGateScreen blocks minors or missing legal consent and submits valid adults`() {
        var submittedDob: String? = null
        var submittedTerms = false
        var submittedPrivacy = false

        composeRule.setContent {
            AyerTheme {
                AgeVerificationGateScreen(
                    userProfile = UserProfile(name = "أحمد"),
                    ageSubmission = null,
                    isSubmitting = false,
                    errorMessage = null,
                    onSubmitAgeVerification = { dob, terms, privacy ->
                        submittedDob = dob
                        submittedTerms = terms
                        submittedPrivacy = privacy
                    },
                    onSignOut = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("age_verification_gate_screen").assertIsDisplayed()

        // Try minor DOB with consent -> should NOT submit
        composeRule.onNodeWithTag("dob_input").performTextInput("2012-01-01")
        composeRule.onNodeWithTag("accept_terms_checkbox").performClick()
        composeRule.onNodeWithTag("accept_privacy_checkbox").performClick()
        composeRule.onNodeWithTag("submit_age_verification_button").performClick()
        composeRule.waitForIdle()
        assertEquals(null, submittedDob)

        // Replace with adult DOB and submit -> should invoke callback
        composeRule.onNodeWithTag("dob_input").performTextReplacement("1996-04-20")
        composeRule.onNodeWithTag("submit_age_verification_button").performClick()
        composeRule.waitForIdle()
        assertEquals("1996-04-20", submittedDob)
        assertTrue(submittedTerms)
        assertTrue(submittedPrivacy)
    }
}
