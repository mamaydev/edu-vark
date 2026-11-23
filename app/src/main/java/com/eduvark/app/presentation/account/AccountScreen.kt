package com.eduvark.app.presentation.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduvark.app.BuildConfig
import com.eduvark.app.domain.model.UserProfile
import com.eduvark.app.domain.model.UserRole
import com.eduvark.app.ui.components.AdminBottomBar
import com.eduvark.app.ui.components.AdminTab
import com.eduvark.app.ui.components.EduCard
import com.eduvark.app.ui.components.EduScreen
import com.eduvark.app.ui.components.ErrorBlock
import com.eduvark.app.ui.components.Eyebrow
import com.eduvark.app.ui.components.HeroCard
import com.eduvark.app.ui.components.LecturerBottomBar
import com.eduvark.app.ui.components.LecturerTab
import com.eduvark.app.ui.components.LoadingBlock
import com.eduvark.app.ui.components.NoticeCard
import com.eduvark.app.ui.components.ScrollColumn
import com.eduvark.app.ui.components.SecondaryButton
import com.eduvark.app.ui.components.StudentBottomBar
import com.eduvark.app.ui.components.StudentTab
import com.eduvark.app.ui.components.TextLink
import com.eduvark.app.ui.components.TintBar
import com.eduvark.app.ui.components.TintTone
import com.eduvark.app.ui.theme.EduVarkTheme
import com.eduvark.app.ui.theme.EduTheme
import com.eduvark.app.R
import com.eduvark.app.ui.theme.dimen
import com.eduvark.app.ui.theme.dimenSp

@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenStudentTopics: () -> Unit,
    onOpenLecturerTopics: () -> Unit,
    onOpenGrading: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenAdminAccounts: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenChangePassword: () -> Unit = {},
    onOpenServerSettings: () -> Unit = {},
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) {
            viewModel.consumeLoggedOut()
            onLoggedOut()
        }
    }

    EduScreen(
        title = stringResource(R.string.account_title),
        onBack = onBack,
        modifier = modifier,
        bottomBar = {
            when (state.user?.role) {
                UserRole.STUDENT -> StudentBottomBar(StudentTab.AKUN) { tab ->
                    when (tab) {
                        StudentTab.BERANDA -> onOpenHome()
                        StudentTab.TOPIK -> onOpenStudentTopics()
                        StudentTab.AKUN -> Unit
                    }
                }

                UserRole.LECTURER -> LecturerBottomBar(LecturerTab.AKUN) { tab ->
                    when (tab) {
                        LecturerTab.BERANDA -> onOpenHome()
                        LecturerTab.TOPIK -> onOpenLecturerTopics()
                        LecturerTab.PENILAIAN -> onOpenGrading()
                        LecturerTab.ANALITIK -> onOpenAnalytics()
                        LecturerTab.AKUN -> Unit
                    }
                }

                UserRole.ADMIN -> AdminBottomBar(AdminTab.PROFIL) { tab ->
                    when (tab) {
                        AdminTab.BERANDA -> onOpenHome()
                        AdminTab.AKUN -> onOpenAdminAccounts()
                        AdminTab.PROFIL -> Unit
                    }
                }

                null -> Unit
            }
        },
    ) { padding ->
        val user = state.user
        when {
            state.isLoading && user == null -> LoadingBlock(Modifier.padding(padding))

            user == null -> Column(Modifier.padding(padding).padding(dimen(R.dimen.space_24))) {
                ErrorBlock(
                    message = state.error ?: stringResource(R.string.account_not_found),
                    onRetry = viewModel::load,
                )
            }

            else -> ScrollColumn(modifier = Modifier.padding(padding), spacing = dimen(R.dimen.space_0)) {
                Eyebrow(user.role.label)
                Spacer(Modifier.height(dimen(R.dimen.space_10)))
                Text(text = accountHeadline(user.role), style = EduTheme.type.display)
                Spacer(Modifier.height(dimen(R.dimen.space_10)))
                Text(text = accountSubtitle(user.role), style = EduTheme.type.body)
                Spacer(Modifier.height(dimen(R.dimen.space_22)))

                EduCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = user.fullName, style = EduTheme.type.headline)
                    Spacer(Modifier.height(dimen(R.dimen.space_6)))
                    Text(text = user.loginId, style = EduTheme.type.bodySmall)
                    Spacer(Modifier.height(dimen(R.dimen.space_12)))
                    TintBar(
                        text = stringResource(R.string.account_active_status, user.role.label),
                        tone = TintTone.Primary,
                        background = EduTheme.colors.purpleTint,
                        contentColor = EduTheme.colors.lime,
                    )
                }

                Spacer(Modifier.height(dimen(R.dimen.space_16)))
                HeroCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (user.role == UserRole.STUDENT) {
                            stringResource(R.string.account_session_eyebrow_vark)
                        } else {
                            stringResource(R.string.account_session_eyebrow_access)
                        },
                        style = EduTheme.type.eyebrow.copy(color = EduTheme.colors.onBrand.copy(alpha = 0.72f)),
                    )
                    Spacer(Modifier.height(dimen(R.dimen.space_10)))
                    Text(
                        text = sessionTitle(user),
                        style = EduTheme.type.headline.copy(color = EduTheme.colors.onBrand, fontSize = dimenSp(R.dimen.text_size_24)),
                    )
                    Spacer(Modifier.height(dimen(R.dimen.space_10)))
                    Text(
                        text = sessionBody(user),
                        style = EduTheme.type.bodySmall.copy(color = EduTheme.colors.onBrand.copy(alpha = 0.9f)),
                    )
                }

                Spacer(Modifier.height(dimen(R.dimen.space_16)))
                NoticeCard(text = accountNotice(user.role))

                Spacer(Modifier.height(dimen(R.dimen.space_20)))
                SecondaryButton(
                    text = stringResource(R.string.account_change_password),
                    onClick = onOpenChangePassword,
                )
                Spacer(Modifier.height(dimen(R.dimen.space_10)))
                SecondaryButton(
                    text = stringResource(R.string.account_logout),
                    onClick = viewModel::logout,
                )
                Spacer(Modifier.height(dimen(R.dimen.space_12)))
                Text(
                    text = stringResource(R.string.account_logout_note),
                    style = EduTheme.type.bodySmall,
                )

                if (BuildConfig.DEBUG) {
                    Spacer(Modifier.height(dimen(R.dimen.space_16)))
                    TextLink(
                        text = stringResource(R.string.common_server_settings_debug),
                        onClick = onOpenServerSettings,
                        icon = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun accountHeadline(role: UserRole): String = when (role) {
    UserRole.STUDENT -> stringResource(R.string.account_headline_student)
    UserRole.LECTURER -> stringResource(R.string.account_headline_lecturer)
    UserRole.ADMIN -> stringResource(R.string.account_headline_admin)
}

@Composable
private fun accountSubtitle(role: UserRole): String = when (role) {
    UserRole.STUDENT -> stringResource(R.string.account_subtitle_student)
    UserRole.LECTURER -> stringResource(R.string.account_subtitle_lecturer)
    UserRole.ADMIN -> stringResource(R.string.account_subtitle_admin)
}

@Composable
private fun sessionTitle(user: UserProfile): String = when (user.role) {
    UserRole.STUDENT -> stringResource(
        R.string.account_session_title_student,
        user.activeVark?.label ?: stringResource(R.string.common_vark),
    )
    UserRole.LECTURER -> stringResource(R.string.account_session_title_lecturer)
    UserRole.ADMIN -> stringResource(R.string.account_session_title_admin)
}

@Composable
private fun sessionBody(user: UserProfile): String = when (user.role) {
    UserRole.STUDENT -> stringResource(R.string.account_session_body_student)
    UserRole.LECTURER -> stringResource(R.string.account_session_body_lecturer)
    UserRole.ADMIN -> stringResource(R.string.account_session_body_admin)
}

@Composable
private fun accountNotice(role: UserRole): String = when (role) {
    UserRole.STUDENT -> stringResource(R.string.account_notice_student)
    UserRole.LECTURER -> stringResource(R.string.account_notice_lecturer)
    UserRole.ADMIN -> stringResource(R.string.account_notice_admin)
}

@Preview(showBackground = true)
@Composable
private fun AccountScreenPreview() {
    EduVarkTheme {
        AccountScreen(
            onBack = {},
            onLoggedOut = {},
            onOpenHome = {},
            onOpenStudentTopics = {},
            onOpenLecturerTopics = {},
            onOpenGrading = {},
            onOpenAnalytics = {},
            onOpenAdminAccounts = {},
        )
    }
}
