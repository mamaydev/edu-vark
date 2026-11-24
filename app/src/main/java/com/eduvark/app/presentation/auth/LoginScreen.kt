package com.eduvark.app.presentation.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduvark.app.BuildConfig
import com.eduvark.app.core.common.LoginIdRules
import com.eduvark.app.ui.components.EduField
import com.eduvark.app.ui.components.PasswordField
import com.eduvark.app.ui.components.EduScreen
import com.eduvark.app.ui.components.Eyebrow
import com.eduvark.app.ui.components.FieldHelper
import com.eduvark.app.ui.components.FieldLabel
import com.eduvark.app.ui.components.HeroCard
import com.eduvark.app.ui.components.NoticeCard
import com.eduvark.app.ui.components.PrimaryButton
import com.eduvark.app.ui.components.ScrollColumn
import com.eduvark.app.ui.components.TextLink
import com.eduvark.app.ui.components.TintBar
import com.eduvark.app.ui.components.TintTone
import com.eduvark.app.ui.theme.EduVarkTheme
import com.eduvark.app.ui.theme.EduTheme
import com.eduvark.app.R
import com.eduvark.app.ui.theme.dimen

@Composable
fun LoginScreen(
    onLoginSuccess: (mustChangePassword: Boolean, varkRequired: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onOpenServerSettings: () -> Unit = {},
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var loginId by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.success) {
        state.success?.let { success ->
            viewModel.consumeSuccess()
            onLoginSuccess(success.mustChangePassword, success.varkRequired)
        }
    }

    if (state.blocked) {
        InactiveAccountScreen(
            onBack = { viewModel.clearBlocked() },
            modifier = modifier,
        )
        return
    }

    EduScreen(title = stringResource(R.string.app_name), brand = true, modifier = modifier) { padding ->
        ScrollColumn(modifier = Modifier.padding(padding), spacing = dimen(R.dimen.space_0)) {
            Spacer(Modifier.height(dimen(R.dimen.space_4)))
            HeroCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.auth_hero_eyebrow),
                    style = EduTheme.type.eyebrow.copy(
                        color = EduTheme.colors.onBrand.copy(alpha = 0.72f),
                    ),
                )
                Spacer(Modifier.height(dimen(R.dimen.space_12)))
                Text(text = stringResource(R.string.auth_hero_title), style = EduTheme.type.heroTitle)
            }

            Spacer(Modifier.height(dimen(R.dimen.space_28)))
            Eyebrow(stringResource(R.string.auth_welcome_eyebrow))
            Spacer(Modifier.height(dimen(R.dimen.space_10)))
            Text(text = stringResource(R.string.auth_title), style = EduTheme.type.display)

            Spacer(Modifier.height(dimen(R.dimen.space_24)))
            Column {
                FieldLabel(stringResource(R.string.auth_login_id_label))
                EduField(
                    value = loginId,
                    onValueChange = { loginId = LoginIdRules.filterDigits(it) },
                    placeholder = stringResource(R.string.auth_login_id_placeholder),
                    enabled = !state.isLoading,
                    keyboardType = KeyboardType.Number,
                )
                FieldHelper(text = stringResource(R.string.p3_login_helper_digits))
            }

            Spacer(Modifier.height(dimen(R.dimen.space_16)))
            Column {
                FieldLabel(stringResource(R.string.auth_password_label))
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = stringResource(R.string.auth_password_placeholder),
                    enabled = !state.isLoading,
                    leadingLabel = stringResource(R.string.auth_password_leading),
                )
            }

            val error = state.error
            if (error != null) {
                Spacer(Modifier.height(dimen(R.dimen.space_16)))
                NoticeCard(text = error, tone = TintTone.Danger)
            }

            Spacer(Modifier.height(dimen(R.dimen.space_22)))
            PrimaryButton(
                text = if (state.isLoading) {
                    stringResource(R.string.auth_submit_loading)
                } else {
                    stringResource(R.string.auth_submit)
                },
                onClick = { viewModel.submit(loginId, password) },
                enabled = LoginIdRules.canSubmit(loginId),
                loading = state.isLoading,
                showArrow = !state.isLoading,
            )
            if (!LoginIdRules.canSubmit(loginId)) {
                Spacer(Modifier.height(dimen(R.dimen.space_8)))
                FieldHelper(
                    text = stringResource(R.string.p3_login_submit_hint),
                    isError = false,
                )
            }

            if (BuildConfig.DEBUG) {
                Spacer(Modifier.height(dimen(R.dimen.space_14)))
                TextLink(
                    text = stringResource(R.string.common_server_settings_debug),
                    onClick = onOpenServerSettings,
                    icon = false,
                )
            }
        }
    }
}

/** Mockup 38: a rejected inactive account gets its own status screen. */
@Composable
private fun InactiveAccountScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EduScreen(title = stringResource(R.string.auth_inactive_status_title), modifier = modifier) { padding ->
        ScrollColumn(modifier = Modifier.padding(padding), spacing = dimen(R.dimen.space_0)) {
            Spacer(Modifier.height(dimen(R.dimen.space_8)))
            TintBar(
                text = stringResource(R.string.auth_inactive_tintbar),
                tone = TintTone.Danger,
                uppercase = true,
            )
            Spacer(Modifier.height(dimen(R.dimen.space_20)))
            Eyebrow(stringResource(R.string.auth_inactive_eyebrow))
            Spacer(Modifier.height(dimen(R.dimen.space_10)))
            Text(text = stringResource(R.string.auth_inactive_headline), style = EduTheme.type.display)
            Spacer(Modifier.height(dimen(R.dimen.space_12)))
            Text(text = stringResource(R.string.auth_inactive_body), style = EduTheme.type.body)
            Spacer(Modifier.height(dimen(R.dimen.space_24)))
            NoticeCard(
                title = stringResource(R.string.auth_inactive_notice_title),
                text = stringResource(R.string.auth_inactive_notice_text),
                tone = TintTone.Danger,
            )
            Spacer(Modifier.height(dimen(R.dimen.space_14)))
            NoticeCard(text = stringResource(R.string.auth_inactive_notice_second))
            Spacer(Modifier.height(dimen(R.dimen.space_26)))
            PrimaryButton(text = stringResource(R.string.auth_inactive_back), onClick = onBack)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    EduVarkTheme {
        LoginScreen(onLoginSuccess = { _, _ -> })
    }
}
