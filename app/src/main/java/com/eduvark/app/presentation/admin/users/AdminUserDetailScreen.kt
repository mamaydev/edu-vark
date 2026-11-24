package com.eduvark.app.presentation.admin.users

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
import com.eduvark.app.domain.model.AdminUser
import com.eduvark.app.domain.model.EnrollmentStatus
import com.eduvark.app.domain.model.UserRole
import com.eduvark.app.ui.components.DangerButton
import com.eduvark.app.ui.components.EduCard
import com.eduvark.app.ui.components.EduDropdown
import com.eduvark.app.ui.components.EduField
import com.eduvark.app.ui.components.PasswordField
import com.eduvark.app.ui.components.EduScreen
import com.eduvark.app.ui.components.ErrorBlock
import com.eduvark.app.ui.components.Eyebrow
import com.eduvark.app.ui.components.FieldHelper
import com.eduvark.app.ui.components.FieldLabel
import com.eduvark.app.ui.components.LoadingBlock
import com.eduvark.app.ui.components.NoticeCard
import com.eduvark.app.ui.components.PrimaryButton
import com.eduvark.app.ui.components.ScrollColumn
import com.eduvark.app.ui.components.SecondaryButton
import com.eduvark.app.ui.components.TextLink
import com.eduvark.app.ui.components.TintBar
import com.eduvark.app.ui.components.TintTone
import com.eduvark.app.ui.components.TonePanel
import com.eduvark.app.ui.theme.EduTheme
import com.eduvark.app.R
import com.eduvark.app.ui.theme.dimen

@Composable
fun AdminUserDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    viewModel: AdminUserDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val user = state.user
    var fullName by rememberSaveable { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRole.STUDENT) }
    var targetLoginId by rememberSaveable { mutableStateOf("") }
    var confirmingDeactivate by rememberSaveable { mutableStateOf(false) }
    var enrollmentConfirmOpen by rememberSaveable { mutableStateOf(false) }
    var resetOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(user?.id) {
        user?.let {
            fullName = it.fullName
            role = it.role
            targetLoginId = ""
        }
    }

    val inactive = user?.status == AdminAccountStatus.INACTIVE
    val crossingToAcademic = user?.role == UserRole.ADMIN && role != UserRole.ADMIN
    val crossingToAdmin = user != null && user.role != UserRole.ADMIN && role == UserRole.ADMIN
    val confirming = confirmingDeactivate && user != null && !inactive
    val roleLabels = UserRole.entries.map { it.label }
    val statusLabels = AdminAccountStatus.entries.map { it.label }

    EduScreen(
        title = when {
            inactive -> stringResource(R.string.admin_status_page_title)
            confirming -> stringResource(R.string.admin_deactivate_action)
            else -> stringResource(R.string.admin_detail_title)
        },
        onBack = {
            if (confirming) {
                confirmingDeactivate = false
            } else {
                onBack()
            }
        },
        modifier = modifier,
        bottomBar = bottomBar,
    ) { padding ->
        when {
            state.isLoading && user == null -> {
                LoadingBlock(Modifier.padding(padding))
            }

            user == null -> {
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .padding(dimen(R.dimen.space_24)),
                ) {
                    ErrorBlock(
                        message = state.error ?: stringResource(R.string.admin_user_not_found),
                        onRetry = { viewModel.load() },
                    )
                }
            }

            inactive -> {
                InactiveAccountState(
                    onBack = onBack,
                    modifier = Modifier.padding(padding),
                )
            }

            confirming -> {
                ConfirmDeactivateState(
                    user = user,
                    isSaving = state.isSaving,
                    error = state.actionError,
                    onCancel = { confirmingDeactivate = false },
                    onConfirm = viewModel::deactivate,
                    modifier = Modifier.padding(padding),
                )
            }

            else -> {
                ScrollColumn(modifier = Modifier.padding(padding), spacing = dimen(R.dimen.space_0)) {
                    TintBar(
                        text = stringResource(
                            R.string.admin_user_role_status,
                            user.role.label,
                            user.status.label,
                        ).uppercase(),
                        tone = if (user.status == AdminAccountStatus.ACTIVE) {
                            TintTone.Teal
                        } else {
                            TintTone.Danger
                        },
                    )
                    Spacer(Modifier.height(dimen(R.dimen.space_16)))
                    Text(text = user.fullName, style = EduTheme.type.display)
                    Spacer(Modifier.height(dimen(R.dimen.space_10)))
                    Text(
                        text = stringResource(R.string.admin_detail_subtitle),
                        style = EduTheme.type.body,
                    )

                    Spacer(Modifier.height(dimen(R.dimen.space_22)))
                    FieldLabel(stringResource(R.string.admin_full_name))
                    EduField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        placeholder = stringResource(R.string.admin_full_name),
                        enabled = !state.isSaving,
                    )

                    Spacer(Modifier.height(dimen(R.dimen.space_16)))
                    FieldLabel(stringResource(R.string.admin_login_id_label))
                    EduField(
                        value = user.loginId,
                        onValueChange = { },
                        enabled = false,
                    )

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
                    if (crossingToAcademic) {
                        Spacer(Modifier.height(dimen(R.dimen.space_16)))
                        FieldLabel(stringResource(R.string.p3_admin_target_login_label))
                        EduField(
                            value = targetLoginId,
                            onValueChange = { targetLoginId = LoginIdRules.filterDigits(it) },
                            placeholder = stringResource(R.string.p3_admin_target_login_label),
                            enabled = !state.isSaving,
                            keyboardType = KeyboardType.Number,
                        )
                        FieldHelper(stringResource(R.string.p3_admin_target_login_helper))
                    } else if (crossingToAdmin) {
                        Spacer(Modifier.height(dimen(R.dimen.space_16)))
                        NoticeCard(
                            text = stringResource(R.string.p3_admin_role_change_auto_note),
                            tone = TintTone.Teal,
                        )
                    }

                    Spacer(Modifier.height(dimen(R.dimen.space_16)))
                    FieldLabel(stringResource(R.string.admin_field_status))
                    EduDropdown(
                        value = user.status.label,
                        options = statusLabels,
                        onSelect = { label ->
                            if (label == AdminAccountStatus.INACTIVE.label) {
                                confirmingDeactivate = true
                            }
                        },
                        enabled = !state.isSaving,
                    )

                    Spacer(Modifier.height(dimen(R.dimen.space_18)))
                    NoticeCard(
                        text = stringResource(R.string.admin_update_notice),
                        tone = TintTone.Primary,
                    )

                    val loadError = state.error
                    if (loadError != null) {
                        Spacer(Modifier.height(dimen(R.dimen.space_16)))
                        NoticeCard(text = loadError, tone = TintTone.Danger)
                    }
                    val actionMessage = state.actionMessage
                    if (actionMessage != null) {
                        Spacer(Modifier.height(dimen(R.dimen.space_16)))
                        NoticeCard(text = actionMessage, tone = TintTone.Teal)
                    }
                    val actionError = state.actionError
                    if (actionError != null) {
                        Spacer(Modifier.height(dimen(R.dimen.space_16)))
                        NoticeCard(text = actionError, tone = TintTone.Danger)
                    }

                    Spacer(Modifier.height(dimen(R.dimen.space_20)))
                    PrimaryButton(
                        text = stringResource(R.string.admin_save_changes),
                        onClick = { viewModel.updateProfile(fullName, role, targetLoginId) },
                        enabled = !state.isSaving,
                        loading = state.isSaving,
                    )
                    Spacer(Modifier.height(dimen(R.dimen.space_12)))
                    SecondaryButton(
                        text = stringResource(R.string.admin_deactivate_action),
                        onClick = { confirmingDeactivate = true },
                        enabled = !state.isSaving,
                    )

                    Spacer(Modifier.height(dimen(R.dimen.space_18)))
                    if (resetOpen) {
                        ResetPasswordPanel(
                            enabled = !state.isSaving,
                            onSave = { temporaryPassword ->
                                resetOpen = false
                                viewModel.resetPassword(temporaryPassword)
                            },
                            onCancel = { resetOpen = false },
                        )
                    } else {
                        TextLink(
                            text = stringResource(R.string.admin_reset_password_link),
                            onClick = {
                                resetOpen = true
                                enrollmentConfirmOpen = false
                            },
                            icon = false,
                        )
                    }

                    if (user.role == UserRole.STUDENT && user.enrollmentId != null) {
                        val enrollmentActive = user.enrollmentStatus == EnrollmentStatus.ACTIVE
                        Spacer(Modifier.height(dimen(R.dimen.space_10)))
                        if (enrollmentConfirmOpen) {
                            TonePanel(
                                title = if (enrollmentActive) {
                                    stringResource(R.string.admin_enrollment_remove_title)
                                } else {
                                    stringResource(R.string.admin_enrollment_activate_title)
                                },
                                body = if (enrollmentActive) {
                                    stringResource(R.string.admin_enrollment_remove_body)
                                } else {
                                    stringResource(R.string.admin_enrollment_activate_body)
                                },
                                tone = if (enrollmentActive) TintTone.Amber else TintTone.Primary,
                            )
                            Spacer(Modifier.height(dimen(R.dimen.space_12)))
                            SecondaryButton(
                                text = stringResource(R.string.admin_action_cancel),
                                onClick = { enrollmentConfirmOpen = false },
                                enabled = !state.isSaving,
                            )
                            Spacer(Modifier.height(dimen(R.dimen.space_10)))
                            if (enrollmentActive) {
                                DangerButton(
                                    text = stringResource(R.string.admin_enrollment_remove_action),
                                    onClick = {
                                        enrollmentConfirmOpen = false
                                        viewModel.setEnrollment(false)
                                    },
                                    enabled = !state.isSaving,
                                )
                            } else {
                                PrimaryButton(
                                    text = stringResource(R.string.admin_enrollment_activate_action),
                                    onClick = {
                                        enrollmentConfirmOpen = false
                                        viewModel.setEnrollment(true)
                                    },
                                    enabled = !state.isSaving,
                                )
                            }
                        } else {
                            TextLink(
                                text = if (enrollmentActive) {
                                    stringResource(R.string.admin_enrollment_remove_action)
                                } else {
                                    stringResource(R.string.admin_enrollment_reactivate_link)
                                },
                                onClick = {
                                    enrollmentConfirmOpen = true
                                    resetOpen = false
                                },
                                color = if (enrollmentActive) {
                                    EduTheme.colors.danger
                                } else {
                                    EduTheme.colors.lavenderDim
                                },
                                icon = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmDeactivateState(
    user: AdminUser,
    isSaving: Boolean,
    error: String?,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollColumn(modifier = modifier, spacing = dimen(R.dimen.dimen_0)) {
        Eyebrow(stringResource(R.string.admin_confirm_eyebrow))
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(
            text = stringResource(R.string.admin_confirm_headline),
            style = EduTheme.type.display,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(
            text = stringResource(R.string.admin_confirm_body),
            style = EduTheme.type.body,
        )

        Spacer(Modifier.height(dimen(R.dimen.space_22)))
        EduCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = EduTheme.colors.dangerTint,
            padding = dimen(R.dimen.dimen_20),
        ) {
            Text(
                text = user.fullName,
                style = EduTheme.type.headline.copy(color = EduTheme.colors.dangerMuted),
            )
            Spacer(Modifier.height(dimen(R.dimen.space_8)))
            Text(
                text = user.loginId,
                style = EduTheme.type.bodySmall.copy(color = EduTheme.colors.dangerMuted),
            )
            Spacer(Modifier.height(dimen(R.dimen.space_4)))
            Text(
                text = stringResource(
                    R.string.admin_confirm_role_status,
                    user.role.label,
                    user.status.label.lowercase(),
                ),
                style = EduTheme.type.bodySmall.copy(color = EduTheme.colors.dangerMuted),
            )
        }

        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        NoticeCard(
            text = stringResource(R.string.admin_confirm_notice),
            tone = TintTone.Amber,
        )

        if (error != null) {
            Spacer(Modifier.height(dimen(R.dimen.space_16)))
            NoticeCard(text = error, tone = TintTone.Danger)
        }

        Spacer(Modifier.height(dimen(R.dimen.space_22)))
        PrimaryButton(
            text = stringResource(R.string.admin_action_cancel),
            onClick = onCancel,
            enabled = !isSaving,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_12)))
        DangerButton(
            text = if (isSaving) {
                stringResource(R.string.admin_deactivating)
            } else {
                stringResource(R.string.admin_confirm_deactivate)
            },
            onClick = onConfirm,
            enabled = !isSaving,
        )
    }
}

@Composable
private fun InactiveAccountState(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollColumn(modifier = modifier, spacing = dimen(R.dimen.dimen_0)) {
        TintBar(
            text = stringResource(R.string.admin_login_rejected),
            tone = TintTone.Danger,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_18)))
        Eyebrow(stringResource(R.string.admin_inactive_title))
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(
            text = stringResource(R.string.admin_inactive_headline),
            style = EduTheme.type.display,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(
            text = stringResource(R.string.admin_inactive_body),
            style = EduTheme.type.body,
        )

        Spacer(Modifier.height(dimen(R.dimen.space_22)))
        EduCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = EduTheme.colors.dangerTint,
            padding = dimen(R.dimen.dimen_20),
        ) {
            Text(
                text = stringResource(R.string.admin_inactive_title),
                style = EduTheme.type.headline.copy(color = EduTheme.colors.dangerMuted),
            )
            Spacer(Modifier.height(dimen(R.dimen.space_10)))
            Text(
                text = stringResource(R.string.admin_inactive_help),
                style = EduTheme.type.bodySmall.copy(color = EduTheme.colors.dangerMuted),
            )
        }

        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        NoticeCard(
            text = stringResource(R.string.admin_inactive_notice),
            tone = TintTone.Primary,
        )

        Spacer(Modifier.height(dimen(R.dimen.space_22)))
        PrimaryButton(
            text = stringResource(R.string.admin_back_to_login),
            onClick = onBack,
        )
    }
}

@Composable
private fun ResetPasswordPanel(
    enabled: Boolean,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    val passwordMinMessage = stringResource(R.string.admin_error_password_min)
    val passwordMismatchMessage = stringResource(R.string.admin_error_password_mismatch)
    val save: () -> Unit = {
        error = when {
            password.length < 6 -> passwordMinMessage
            password != confirmation -> passwordMismatchMessage
            else -> null
        }
        if (error == null) onSave(password)
    }

    EduCard(
        modifier = Modifier.fillMaxWidth(),
        padding = dimen(R.dimen.dimen_18),
    ) {
        Text(text = stringResource(R.string.admin_reset_password_title), style = EduTheme.type.section)
        Spacer(Modifier.height(dimen(R.dimen.space_6)))
        Text(
            text = stringResource(R.string.admin_reset_password_body),
            style = EduTheme.type.bodySmall,
        )

        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        FieldLabel(stringResource(R.string.admin_temp_password_label))
        PasswordField(
            value = password,
            onValueChange = {
                password = it
                error = null
            },
            placeholder = stringResource(R.string.admin_password_min_placeholder),
            enabled = enabled,
            leadingLabel = stringResource(R.string.admin_temp_password_semantics),
        )

        Spacer(Modifier.height(dimen(R.dimen.space_12)))
        FieldLabel(stringResource(R.string.admin_temp_password_confirm_label))
        PasswordField(
            value = confirmation,
            onValueChange = {
                confirmation = it
                error = null
            },
            placeholder = stringResource(R.string.admin_password_repeat_placeholder),
            enabled = enabled,
            leadingLabel = stringResource(R.string.admin_temp_password_confirm_semantics),
        )

        val errorText = error
        if (errorText != null) {
            FieldHelper(text = errorText, isError = true)
        }

        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        PrimaryButton(
            text = stringResource(R.string.admin_save_temp_password),
            onClick = save,
            enabled = enabled,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        SecondaryButton(
            text = stringResource(R.string.admin_action_cancel),
            onClick = onCancel,
            enabled = enabled,
        )
    }
}
