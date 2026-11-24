package com.eduvark.app.presentation.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eduvark.app.R
import com.eduvark.app.domain.usecase.LoginUseCase
import com.eduvark.app.testutil.FakeAuthRepository
import com.eduvark.app.ui.theme.EduVarkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loginScreenShowsCredentialsAndSubmitButton() {
        val viewModel = LoginViewModel(LoginUseCase(FakeAuthRepository()))

        composeRule.setContent {
            EduVarkTheme {
                LoginScreen(
                    onLoginSuccess = { _, _ -> },
                    viewModel = viewModel,
                )
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithText(
            context.getString(R.string.auth_login_id_placeholder),
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Password").assertIsDisplayed()
        composeRule.onNodeWithText("Masuk").assertIsDisplayed()
    }

    @Test
    fun loginScreenKeepsOnlyDigitsWhileTyping() {
        val viewModel = LoginViewModel(LoginUseCase(FakeAuthRepository()))
        setLoginContent(viewModel)

        val loginField = composeRule.onAllNodes(hasSetTextAction())[0]
        loginField.performTextInput("ab12cd34567890xyz")

        loginField.assertTextContains("1234567890")
    }

    @Test
    fun loginScreenCleansPastedTextAndStopsAtElevenDigits() {
        val viewModel = LoginViewModel(LoginUseCase(FakeAuthRepository()))
        setLoginContent(viewModel)

        val loginField = composeRule.onAllNodes(hasSetTextAction())[0]
        loginField.performTextReplacement("20ab25-0000 011199")

        loginField.assertTextContains("20250000011")
    }

    @Test
    fun loginSubmitIsEnabledOnlyForTenOrElevenDigits() {
        val viewModel = LoginViewModel(LoginUseCase(FakeAuthRepository()))
        setLoginContent(viewModel)

        val submit = composeRule.onNodeWithText("Masuk")
        submit.assertIsNotEnabled()

        val loginField = composeRule.onAllNodes(hasSetTextAction())[0]
        loginField.performTextInput("202500000")
        submit.assertIsNotEnabled()

        loginField.performTextInput("1")
        submit.assertIsEnabled()

        // Input paste/ketik beruntun dipotong ke 11 digit pertama (FR-001).
        loginField.performTextInput("23")
        loginField.assertTextContains("20250000012")
        submit.assertIsEnabled()
    }

    private fun setLoginContent(viewModel: LoginViewModel) {
        composeRule.setContent {
            EduVarkTheme {
                LoginScreen(
                    onLoginSuccess = { _, _ -> },
                    viewModel = viewModel,
                )
            }
        }
    }
}
