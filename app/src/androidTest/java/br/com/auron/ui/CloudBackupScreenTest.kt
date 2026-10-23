package br.com.auron.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import br.com.auron.cloud.CloudUiState
import br.com.auron.ui.theme.AuronTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CloudBackupScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun openingDoesNotUploadAndUninspectedStateCannotUpload() {
        var uploads = 0
        compose.setContent { AuronTheme { CloudBackupScreen(CloudUiState(), {}, { uploads++ }, {}, {}) } }
        compose.onNodeWithTag("cloud_upload").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("cloud_consent").performScrollTo().performClick()
        compose.onNodeWithTag("cloud_upload").performScrollTo().assertIsNotEnabled()
        assertEquals(0, uploads)
    }

    @Test fun uploadRequiresConsentAndSeparateConfirmation() {
        var uploads = 0
        compose.setContent { AuronTheme { CloudBackupScreen(CloudUiState(inspected = true), {}, { uploads++ }, {}, {}) } }
        compose.onNodeWithTag("cloud_upload").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("cloud_consent").performScrollTo().performClick()
        compose.onNodeWithTag("cloud_upload").performScrollTo().performClick()
        assertEquals(0, uploads)
        compose.onNodeWithText("Confirmar envio").performClick()
        assertEquals(1, uploads)
    }
}
