package com.eduvark.app.presentation.admin.users

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduvark.app.core.common.LoginIdRules
import com.eduvark.app.domain.model.AdminAccountStatus
import com.eduvark.app.domain.model.UserRole
import com.eduvark.app.ui.components.EduCard
import com.eduvark.app.ui.components.EduDropdown
import com.eduvark.app.ui.components.EduField
import com.eduvark.app.ui.components.PasswordField
import com.eduvark.app.ui.components.EduRadioRow
import com.eduvark.app.ui.components.EduScreen
import com.eduvark.app.ui.components.Eyebrow
import com.eduvark.app.ui.components.FieldHelper
import com.eduvark.app.ui.components.FieldLabel
import com.eduvark.app.ui.components.NoticeCard
import com.eduvark.app.ui.components.PrimaryButton
import com.eduvark.app.ui.components.ScrollColumn
import com.eduvark.app.ui.components.SecondaryButton
import com.eduvark.app.ui.components.TintBar
import com.eduvark.app.ui.components.TintTone
import com.eduvark.app.ui.theme.EduTheme
import com.eduvark.app.R
import com.eduvark.app.ui.theme.dimen

@Composable
fun AdminCreateUserScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    viewModel: AdminCreateUserViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var fullName by rememberSaveable { mutableStateOf("") }
    var loginId by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(UserRole.STUDENT) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var duplicateIdentity by remember { mutableStateOf(false) }

    LaunchedEffect(state.success) {
        if (state.success) {
            viewModel.consumeSuccess()
            onCreated()
        }
    }

    LaunchedEffect(state.error) {
        val error = state.error
        if (error != null) duplicateIdentity = isDuplicateIdentityError(error)
    }

    val roleLabels = UserRole.entries.map { it.label }
    val passwordsMismatch = confirmPassword.isNotEmpty() && password != confirmPassword
    val passwordMismatchMessage = stringResource(R.string.admin_error_password_mismatch)
    val submit: () -> Unit = {
        if (password != confirmPassword) {
            localError = passwordMismatchMessage
        } else {
            localError = null
            viewModel.create(loginId, fullName, role, password)
        }
    }

    EduScreen(
        title = stringResource(R.string.admin_create_title),
        onBack = onBack,
        modifier = modifier,
        bottomBar = bottomBar,
    ) { padding ->
        if (duplicateIdentity) {
            ScrollColumn(modifier = Modifier.padding(padding), spacing = dimen(R.dimen.space_0)) {
                TintBar(
                    text = stringResource(R.string.admin_create_validation_bar),
                    tone = TintTone.Danger,
                )
                Spacer(Modifier.height(dimen(R.dimen.space_18)))
                Eyebrow(stringResource(R.string.admin_create_duplicate_eyebrow))
                Spacer(Modifier.height(dimen(R.dimen.space_10)))
                Text(
                    text = stringResource(R.string.admin_create_duplicate_headline),
                    style = EduTheme.type.display,
                )
                Spacer(Modifier.height(dimen(R.dimen.space_10)))
                Text(
                    text = stringResource(R.string.admin_create_duplicate_body),
                    style = EduTheme.type.body,
                )

                Spacer(Modifier.height(dimen(R.dimen.space_22)))
                FieldLabel(stringResource(R.string.admin_full_name))
                EduField(
                    value = fullName,
                    onValueChange = {
                        fullName = it
                        localError = null
                    },
                    placeholder = stringResource(R.string.admin_full_name),
                    enabled = !state.isSaving,
                )

                Spacer(Modifier.height(dimen(R.dimen.space_16)))
                if (role != UserRole.ADMIN) {
                    FieldLabel(stringResource(R.string.p3_admin_login_id_label))
                    EduField(
                        value = loginId,
                        onValueChange = {
                            loginId = LoginIdRules.filterDigits(it)
                            localError = null
                        },
                        placeholder = stringResource(R.string.p3_admin_login_id_placeholder),
                        error = true,
                        enabled = !state.isSaving,
                        keyboardType = KeyboardType.Number,
                    )
                    FieldHelper(
                        text = stringResource(R.string.admin_duplicate_helper),
                        isError = true,
                    )
                } else {
                    NoticeCard(
                        text = stringResource(R.string.p3_admin_auto_id_note),
                        tone = TintTone.Primary,
                    )
                }

                Spacer(Modifier.height(dimen(R.dimen.space_16)))
                FieldLabel(stringResource(R.string.admin_field_role))
                EduDropdown(
                    value = role.label,
                    options = roleLabels,
                    onSelect = { label ->
                        UserRole.entries.firstOrNull { it.label == label }?.let { role = it }
                    },
                    enabled = !state.isSaving,
                )

                Spacer(Modifier.height(dimen(R.dimen.space_20)))
                NoticeCard(
                    text = stringResource(R.string.admin_duplicate_notice),
                    tone = TintTone.Danger,
                )

                Spacer(Modifier.height(dimen(R.dimen.space_22)))
                PrimaryButton(
                    text = stringResource(R.string.admin_fix_and_save),
                    onClick = submit,
                    enabled = !state.isSaving,
                    loading = state.isSaving,
                )
                Spacer(Modifier.height(dimen(R.dimen.space_12)))
                SecondaryButton(
                    text = stringResource(R.string.admin_back_to_users),
                    onClick = onBack,
                    enabled = !state.isSaving,
                )
            }
        } else {
            ScrollColumn(modifier = Modifier.padding(padding), spacing = dimen(R.dimen.space_0)) {
                Eyebrow(stringResource(R.string.admin_create_eyebrow))
                Spacer(Modifier.height(dimen(R.dimen.space_10)))
                Text(
                    text = stringResource(R.string.admin_create_headline),
                    style = EduTheme.type.display,
                )
                Spacer(Modifier.height(dimen(R.dimen.space_10)))
                Text(
                    text = stringResource(R.string.admin_create_subtitle),
                    style = EduTheme.type.body,
                )

                Spacer(Modifier.height(dimen(R.dimen.space_22)))
                FieldLabel(stringResource(R.string.admin_full_name))
                EduField(
                    value = fullName,
                    onValueChange = {
                        fullName = it
                        localError = null
                    },
                    placeholder = stringResource(R.string.admin_full_name),
                    enabled = !state.isSaving,
                )

                Spacer(Modifier.height(dimen(R.dimen.space_16)))
                if (role != UserRole.ADMIN) {
                    FieldLabel(stringResource(R.string.p3_admin_login_id_label))
                    EduField(
                        value = loginId,
                        onValueChange = {
                            loginId = LoginIdRules.filterDigits(it)
                            localError = null
                        },
                        placeholder = stringResource(R.string.p3_admin_login_id_placeholder),
                        enabled = !state.isSaving,
                        keyboardType = KeyboardType.Number,
                    )
                    FieldHelper(text = stringResource(R.string.p3_admin_login_helper_digits))
                } else {
                    NoticeCard(
                        text = stringResource(R.string.p3_admin_auto_id_note),
                        tone = TintTone.Primary,
                    )
                }

                Spacer(Modifier.height(dimen(R.dimen.space_16)))
                FieldLabel(stringResource(R.string.admin_initial_password_label))
                PasswordField(
                    value = password,
                    onValueChange = {
                        password = it
                        localError = null
                    },
                    placeholder = stringResource(R.string.admin_password_min_placeholder),
                    enabled = !state.isSaving,
                    leadingLabel = stringResource(R.string.admin_initial_password_semantics),
                )

                Spacer(Modifier.height(dimen(R.dimen.space_16)))
                FieldLabel(stringResource(R.string.admin_initial_password_confirm_label))
                PasswordField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        localError = null
                    },
                    placeholder = stringResource(R.string.admin_password_repeat_placeholder),
                    enabled = !state.isSaving,
                    leadingLabel = stringResource(R.string.admin_initial_password_confirm_semantics),
                )
                if (passwordsMismatch) {
                    FieldHelper(
                        text = passwordMismatchMessage,
                        isError = true,
                    )
                }

                Spacer(Modifier.height(dimen(R.dimen.space_18)))
                FieldLabel(stringResource(R.string.admin_role_group_label))
                EduCard(
                    modifier = Modifier.fillMaxWidth(),
                    padding = dimen(R.dimen.dimen_16),
                ) {
                    UserRole.entries.forEachIndexed { index, option ->
                        if (index > 0) Spacer(Modifier.height(dimen(R.dimen.space_2)))
                        EduRadioRow(
                            label = option.label,
                            selected = role == option,
                            onClick = {
                                if (!state.isSaving) role = option
                            },
                        )
                    }
                }
                Spacer(Modifier.height(dimen(R.dimen.space_8)))
                FieldHelper(text = roleHint(role))

                Spacer(Modifier.height(dimen(R.dimen.space_16)))
                FieldLabel(stringResource(R.string.admin_status_label))
                EduDropdown(
                    value = AdminAccountStatus.ACTIVE.label,
                    options = listOf(AdminAccountStatus.ACTIVE.label),
                    onSelect = { },
                    enabled = false,
                )

                val errorText = localError ?: state.error
                if (errorText != null) {
                    Spacer(Modifier.height(dimen(R.dimen.space_18)))
                    NoticeCard(text = errorText, tone = TintTone.Danger)
                }

                Spacer(Modifier.height(dimen(R.dimen.space_22)))
                PrimaryButton(
                    text = stringResource(R.string.admin_save_new_user),
                    onClick = submit,
                    enabled = !state.isSaving,
                    loading = state.isSaving,
                )
                Spacer(Modifier.height(dimen(R.dimen.space_12)))
                Text(
                    text = stringResource(R.string.admin_create_footer),
                    style = EduTheme.type.bodySmall,
                )
            }
        }
    }
}

/** Consequence of the selected role, mirrored from the backend role assignment. */
@Composable
private fun roleHint(role: UserRole): String = when (role) {
    UserRole.STUDENT -> stringResource(R.string.admin_role_hint_student)
    UserRole.LECTURER -> stringResource(R.string.admin_role_hint_lecturer)
    UserRole.ADMIN -> stringResource(R.string.admin_role_hint_admin)
}

/**
 * The backend answers a duplicate login with `LOGIN_ID_TAKEN` / "NIM/NIP/NIDN sudah digunakan.";
 * the ViewModel surfaces only the message, so match on the wording it forwards.
 */
private fun isDuplicateIdentityError(message: String?): Boolean {
    if (message.isNullOrBlank()) return false
    val normalized = message.lowercase()
    return normalized.contains("sudah digunakan") ||
        normalized.contains("sudah terdaftar") ||
        normalized.contains("duplikat") ||
        normalized.contains("login_id_taken")
}
