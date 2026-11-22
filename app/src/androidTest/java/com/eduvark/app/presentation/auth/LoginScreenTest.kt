package com.eduvark.app.presentation.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
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

        composeRule.onNodeWithText("NIM / NIP").assertIsDisplayed()
        composeRule.onNodeWithText("Password").assertIsDisplayed()
        composeRule.onNodeWithText("Masuk").assertIsDisplayed()
    }
}
