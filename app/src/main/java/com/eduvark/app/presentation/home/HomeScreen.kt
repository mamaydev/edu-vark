package com.eduvark.app.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduvark.app.domain.model.CaseStudy
import com.eduvark.app.domain.model.SubmissionStatus
import com.eduvark.app.domain.model.Topic
import com.eduvark.app.domain.model.UserRole
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.ui.components.AdminBottomBar
import com.eduvark.app.ui.components.AdminTab
import com.eduvark.app.ui.components.EduIcons
import com.eduvark.app.ui.components.EduPullToRefresh
import com.eduvark.app.ui.components.EduScreen
import com.eduvark.app.ui.components.Eyebrow
import com.eduvark.app.ui.components.LecturerBottomBar
import com.eduvark.app.ui.components.LecturerTab
import com.eduvark.app.ui.components.StudentBottomBar
import com.eduvark.app.ui.components.TextLink
import com.eduvark.app.ui.components.CardEyebrow
import com.eduvark.app.ui.components.StudentTab
import com.eduvark.app.ui.components.EduCard
import com.eduvark.app.ui.components.ErrorBlock
import com.eduvark.app.ui.components.HeroCard
import com.eduvark.app.ui.components.LightButton
import com.eduvark.app.ui.components.LoadingBlock
import com.eduvark.app.ui.components.NoticeCard
import com.eduvark.app.ui.components.PrimaryButton
import com.eduvark.app.ui.components.ScrollColumn
import com.eduvark.app.ui.components.SectionHeader
import com.eduvark.app.ui.components.StatCard
import com.eduvark.app.ui.components.TextLink
import com.eduvark.app.ui.components.TintTone
import com.eduvark.app.ui.components.content
import com.eduvark.app.ui.format.formatDeadline
import androidx.compose.ui.text.font.FontWeight
import com.eduvark.app.ui.components.TopicCard
import com.eduvark.app.ui.components.UserCard
import java.util.Calendar
import com.eduvark.app.ui.theme.EduTheme
import com.eduvark.app.R
import com.eduvark.app.ui.theme.dimen

@Composable
fun HomeScreen(
    onOpenStudentTopics: () -> Unit,
    onOpenLecturerTopics: () -> Unit,
    onOpenAccount: () -> Unit,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenGrading: () -> Unit = {},
    onOpenAnalytics: () -> Unit = {},
    onCreateTopic: () -> Unit = {},
    onCreateUser: () -> Unit = {},
    onOpenAdminAccounts: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) {
            viewModel.consumeLoggedOut()
            onLoggedOut()
        }
    }

    val role = state.user?.role
    EduScreen(
        modifier = modifier,
        title = when (role) {
            UserRole.STUDENT -> stringResource(R.string.home_title_student)
            UserRole.LECTURER -> stringResource(R.string.home_title_lecturer)
            UserRole.ADMIN -> stringResource(R.string.home_title_admin)
            null -> stringResource(R.string.home_title_student)
        },
        topBarAction = {
            if (state.user != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TopBarIcon(
                        icon = EduIcons.Person,
                        description = stringResource(R.string.home_top_bar_account_desc),
                        onClick = onOpenAccount,
                    )
                    TopBarIcon(
                        icon = EduIcons.Logout,
                        description = stringResource(R.string.home_top_bar_logout_desc),
                        onClick = { confirmLogout = true },
                    )
                }
            }
        },
        bottomBar = {
            when (role) {
                UserRole.STUDENT -> StudentBottomBar(StudentTab.BERANDA) { tab ->
                    when (tab) {
                        StudentTab.BERANDA -> Unit
                        StudentTab.TOPIK -> onOpenStudentTopics()
                        StudentTab.AKUN -> onOpenAccount()
                    }
                }

                UserRole.LECTURER -> LecturerBottomBar(LecturerTab.BERANDA) { tab ->
                    when (tab) {
                        LecturerTab.BERANDA -> Unit
                        LecturerTab.TOPIK -> onOpenLecturerTopics()
                        LecturerTab.PENILAIAN -> onOpenGrading()
                        LecturerTab.ANALITIK -> onOpenAnalytics()
                        LecturerTab.AKUN -> onOpenAccount()
                    }
                }

                UserRole.ADMIN -> AdminBottomBar(AdminTab.BERANDA) { tab ->
                    when (tab) {
                        AdminTab.BERANDA -> Unit
                        AdminTab.AKUN -> onOpenAdminAccounts()
                        AdminTab.PROFIL -> onOpenAccount()
                    }
                }

                null -> Unit
            }
        },
    ) { padding ->
        val user = state.user
        when {
            state.isLoading && user == null -> LoadingBlock(Modifier.padding(padding))

            user == null -> Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(dimen(R.dimen.space_24)),
            ) {
                ErrorBlock(
                    message = state.error ?: stringResource(R.string.home_error_session_missing),
                    onRetry = viewModel::load,
                )
            }

            else -> EduPullToRefresh(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.padding(padding),
            ) {
                ScrollColumn {
                if (state.refreshError != null) {
                    NoticeCard(text = state.refreshError.orEmpty(), tone = TintTone.Danger)
                    Spacer(Modifier.height(dimen(R.dimen.space_16)))
                }
                when (user.role) {
                    UserRole.STUDENT -> StudentHome(state, onOpenStudentTopics)
                    UserRole.LECTURER -> LecturerHome(
                        state = state,
                        onOpenTopics = onOpenLecturerTopics,
                        onOpenAnalytics = onOpenAnalytics,
                        onCreateTopic = onCreateTopic,
                    )

                    UserRole.ADMIN -> AdminHome(
                        state = state,
                        onCreateUser = onCreateUser,
                        onOpenAccounts = onOpenAdminAccounts,
                    )
                }
                }
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            containerColor = EduTheme.colors.surface,
            title = { Text(text = stringResource(R.string.home_logout_dialog_title), style = EduTheme.type.headline) },
            text = {
                Text(
                    text = stringResource(R.string.home_logout_dialog_message),
                    style = EduTheme.type.body,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        viewModel.logout()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.home_logout_confirm),
                        style = EduTheme.type.button.copy(color = EduTheme.colors.danger),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) {
                    Text(text = stringResource(R.string.home_cancel), style = EduTheme.type.button.copy(color = EduTheme.colors.muted))
                }
            },
        )
    }
}

@Composable
private fun StudentHome(state: HomeUiState, onOpenTopics: () -> Unit) {
    val user = state.user ?: return
    val firstName = displayFirstName(user.fullName, stringResource(R.string.home_first_name_fallback_student))
    val vark = user.activeVark
    val kinesthetic = vark == VarkType.KINESTHETIC
    val varkLabel = vark?.label ?: stringResource(R.string.home_vark_not_selected)

    Eyebrow(stringResource(R.string.home_greeting, greeting(), firstName))
    Text(
        text = if (kinesthetic) {
            stringResource(R.string.home_display_kinesthetic)
        } else {
            stringResource(R.string.home_display_student)
        },
        style = EduTheme.type.display,
    )

    HeroCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (kinesthetic) {
                stringResource(R.string.home_hero_eyebrow_kinesthetic)
            } else {
                stringResource(R.string.home_hero_eyebrow_student)
            },
            style = EduTheme.type.eyebrow.copy(color = EduTheme.colors.onBrand),
        )
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(
            text = if (kinesthetic) {
                stringResource(R.string.home_hero_title_kinesthetic)
            } else {
                stringResource(R.string.home_class_name)
            },
            style = EduTheme.type.heroTitle,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(
            text = stringResource(R.string.home_hero_vark_locked, varkLabel),
            style = EduTheme.type.bodySmall.copy(color = EduTheme.colors.onBrand),
        )
        if (kinesthetic) {
            Spacer(Modifier.height(dimen(R.dimen.space_6)))
            Text(
                text = stringResource(R.string.home_hero_kinesthetic_note),
                style = EduTheme.type.bodySmall.copy(color = EduTheme.colors.onBrand),
            )
        }
        if (!kinesthetic) {
            Spacer(Modifier.height(dimen(R.dimen.space_18)))
            LightButton(text = stringResource(R.string.home_continue_learning), onClick = onOpenTopics)
        }
    }

    if (!kinesthetic) {
        Row(horizontalArrangement = Arrangement.spacedBy(dimen(R.dimen.space_12))) {
            StatCard(
                value = state.studentDashboard?.availableMaterials
                    ?.toString()
                    ?.padStart(2, '0') ?: stringResource(R.string.home_stat_placeholder),
                label = stringResource(R.string.home_stat_materials_available),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = state.studentDashboard?.notCompletedMaterials
                    ?.toString()
                    ?.padStart(2, '0') ?: stringResource(R.string.home_stat_placeholder),
                label = stringResource(R.string.home_stat_materials_pending),
                modifier = Modifier.weight(1f),
            )
        }
    }

    SectionHeader(
        title = if (kinesthetic) {
            stringResource(R.string.home_section_cases)
        } else {
            stringResource(R.string.home_section_continue_topics)
        },
        actionLabel = stringResource(R.string.home_action_all),
        onAction = onOpenTopics,
    )

    val preview = state.publishedTopics.take(2)
    if (preview.isEmpty() && !state.isLoading) {
        EduCard {
            Text(text = stringResource(R.string.home_empty_topics_title), style = EduTheme.type.cardTitle)
            Spacer(Modifier.height(dimen(R.dimen.space_6)))
            Text(
                text = stringResource(R.string.home_empty_topics_message),
                style = EduTheme.type.bodySmall,
            )
        }
    } else {
        preview.forEachIndexed { index, topic ->
            if (kinesthetic) {
                KinestheticCaseCard(
                    topic = topic,
                    caseStudy = state.casePreviews[topic.id],
                    onClick = onOpenTopics,
                )
            } else {
                TopicPreviewCard(
                    index = index,
                    topic = topic,
                    kinesthetic = kinesthetic,
                    vark = vark,
                    onClick = onOpenTopics,
                )
            }
        }
    }

    if (kinesthetic) {
        NoticeCard(
            text = stringResource(R.string.home_kinesthetic_notice),
        )
    }
}

@Composable
private fun LecturerHome(
    state: HomeUiState,
    onOpenTopics: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onCreateTopic: () -> Unit,
) {
    val user = state.user ?: return
    val firstName = displayFirstName(user.fullName, stringResource(R.string.home_first_name_fallback_lecturer))

    Eyebrow(stringResource(R.string.home_greeting, greeting(), firstName))
    Text(text = stringResource(R.string.home_display_lecturer), style = EduTheme.type.display)

    HeroCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_hero_eyebrow_lecturer),
            style = EduTheme.type.eyebrow.copy(color = EduTheme.colors.onBrand),
        )
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(text = stringResource(R.string.home_class_name), style = EduTheme.type.heroTitle)
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(
            text = stringResource(R.string.home_hero_subtitle_lecturer, state.publishedTopics.size),
            style = EduTheme.type.bodySmall.copy(color = EduTheme.colors.onBrand),
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(dimen(R.dimen.space_12))) {
        StatCard(
            value = state.publishedTopics.size.toString().padStart(2, '0'),
            label = stringResource(R.string.home_stat_published_topics),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = state.draftTopics.size.toString().padStart(2, '0'),
            label = stringResource(R.string.home_stat_drafts),
            modifier = Modifier.weight(1f),
        )
    }

    SectionHeader(title = stringResource(R.string.home_section_priority))

    EduCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenTopics) {
        Text(text = stringResource(R.string.home_card_manage_topics_title), style = EduTheme.type.cardTitle)
        Spacer(Modifier.height(dimen(R.dimen.space_6)))
        Text(
            text = stringResource(R.string.home_card_manage_topics_body),
            style = EduTheme.type.bodySmall,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_12)))
        TextLink(text = stringResource(R.string.home_link_open_topics), onClick = onOpenTopics)
    }

    EduCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = EduTheme.colors.successTint,
        onClick = onOpenAnalytics,
    ) {
        Text(
            text = stringResource(R.string.home_card_analytics_title),
            style = EduTheme.type.cardTitle.copy(color = EduTheme.colors.lime),
        )
        Spacer(Modifier.height(dimen(R.dimen.space_6)))
        Text(
            text = stringResource(R.string.home_card_analytics_body),
            style = EduTheme.type.bodySmall.copy(color = EduTheme.colors.lime),
        )
        Spacer(Modifier.height(dimen(R.dimen.space_12)))
        TextLink(
            text = stringResource(R.string.home_link_open_analytics),
            onClick = onOpenAnalytics,
            color = EduTheme.colors.lime,
        )
    }

    PrimaryButton(text = stringResource(R.string.home_button_create_topic), onClick = onCreateTopic)
}

@Composable
private fun AdminHome(
    state: HomeUiState,
    onCreateUser: () -> Unit,
    onOpenAccounts: () -> Unit,
) {
    val summary = state.adminSummary

    Eyebrow(stringResource(R.string.home_eyebrow_admin))
    Text(text = stringResource(R.string.home_display_admin), style = EduTheme.type.display)

    HeroCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_hero_eyebrow_admin),
            style = EduTheme.type.eyebrow.copy(color = EduTheme.colors.onBrand),
        )
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(
            text = stringResource(R.string.home_hero_accounts_total, summary?.total ?: 0),
            style = EduTheme.type.numeralLarge.copy(color = EduTheme.colors.onBrand),
        )
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(
            text = stringResource(
                R.string.home_hero_accounts_breakdown,
                summary?.students ?: 0,
                summary?.lecturers ?: 0,
                summary?.admins ?: 0,
            ),
            style = EduTheme.type.bodySmall.copy(color = EduTheme.colors.onBrand),
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(dimen(R.dimen.space_12))) {
        StatCard(
            value = (summary?.active ?: 0).toString().padStart(2, '0'),
            label = stringResource(R.string.home_stat_active_accounts),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = (summary?.inactive ?: 0).toString().padStart(2, '0'),
            label = stringResource(R.string.home_stat_inactive_accounts),
            modifier = Modifier.weight(1f),
        )
    }

    PrimaryButton(text = stringResource(R.string.home_button_create_account), onClick = onCreateUser)

    SectionHeader(
        title = stringResource(R.string.home_section_manage_users),
        actionLabel = stringResource(R.string.home_action_all),
        onAction = onOpenAccounts,
    )

    if (state.adminUsers.isEmpty() && !state.isLoading) {
        EduCard {
            Text(text = stringResource(R.string.home_empty_accounts_title), style = EduTheme.type.cardTitle)
            Spacer(Modifier.height(dimen(R.dimen.space_6)))
            Text(
                text = stringResource(R.string.home_empty_accounts_message),
                style = EduTheme.type.bodySmall,
            )
        }
    } else {
        state.adminUsers.take(2).forEach { account ->
            UserCard(
                name = account.fullName.ifBlank { account.loginId },
                meta = stringResource(R.string.home_user_meta, account.loginId, account.role.label),
                status = account.status.label,
                statusTone = if (account.status.wireValue == "ACTIVE") TintTone.Teal else TintTone.Danger,
                onClick = onOpenAccounts,
            )
        }
    }

    NoticeCard(
        text = stringResource(R.string.home_admin_scope_notice),
    )
}

@Composable
private fun KinestheticCaseCard(
    topic: Topic,
    caseStudy: CaseStudy?,
    onClick: () -> Unit,
) {
    val status = caseStudy?.status
    val tone = when (status) {
        SubmissionStatus.SUDAH_DINILAI -> TintTone.Teal
        SubmissionStatus.MENUNGGU_PENILAIAN -> TintTone.Amber
        SubmissionStatus.TIDAK_MENGUMPULKAN -> TintTone.Danger
        else -> TintTone.Neutral
    }
    val statusText = when (status) {
        SubmissionStatus.SUDAH_DINILAI -> stringResource(R.string.home_case_status_graded, caseStudy.score ?: 0)
        SubmissionStatus.MENUNGGU_PENILAIAN -> stringResource(R.string.home_case_status_waiting)
        SubmissionStatus.TIDAK_MENGUMPULKAN -> stringResource(R.string.home_case_status_not_submitted)
        else -> stringResource(R.string.home_case_status_pending)
    }
    val action = when (status) {
        SubmissionStatus.SUDAH_DINILAI -> stringResource(R.string.home_case_action_view_grade)
        SubmissionStatus.MENUNGGU_PENILAIAN -> stringResource(R.string.home_case_action_view_status)
        else -> stringResource(R.string.home_case_action_work)
    }

    EduCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        CardEyebrow(
            stringResource(
                R.string.home_case_eyebrow,
                topic.sortOrder.toString().padStart(2, '0'),
            ),
        )
        Spacer(Modifier.height(dimen(R.dimen.space_8)))
        Text(text = topic.title, style = EduTheme.type.headline)
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(
            text = statusText,
            style = EduTheme.type.bodySmall.copy(color = tone.content(), fontWeight = FontWeight.Bold),
        )
        val deadline = caseStudy?.deadlineAt
        if (deadline != null && status != SubmissionStatus.SUDAH_DINILAI) {
            Spacer(Modifier.height(dimen(R.dimen.space_6)))
            Text(
                text = stringResource(
                    R.string.home_case_deadline,
                    formatDeadline(deadline) ?: caseStudy.deadlineWib,
                ),
                style = EduTheme.type.meta,
            )
        }
        Spacer(Modifier.height(dimen(R.dimen.space_12)))
        TextLink(text = action, onClick = onClick, color = EduTheme.colors.lime)
    }
}

@Composable
private fun TopicPreviewCard(
    index: Int,
    topic: Topic,
    kinesthetic: Boolean,
    vark: VarkType?,
    onClick: () -> Unit,
) {
    val order = (index + 1).coerceAtLeast(topic.sortOrder)
    val varkLabel = vark?.label ?: stringResource(R.string.home_vark_acronym)
    val meta = topic.description?.takeIf { it.isNotBlank() }
        ?: if (kinesthetic) {
            stringResource(R.string.home_topic_meta_kinesthetic)
        } else {
            stringResource(R.string.home_topic_meta_format, varkLabel)
        }
    val statusText = when {
        kinesthetic -> stringResource(R.string.home_topic_status_open_case)
        topic.materialAvailable -> stringResource(R.string.home_topic_status_available, varkLabel)
        else -> stringResource(R.string.home_topic_status_unavailable)
    }
    TopicCard(
        orderLabel = stringResource(R.string.home_topic_order_label, order.toString().padStart(2, '0')),
        title = topic.title,
        meta = meta,
        status = statusText,
        statusTone = when {
            kinesthetic -> TintTone.Teal
            topic.materialAvailable -> TintTone.Teal
            else -> TintTone.Neutral
        },
        onClick = onClick,
    )
}

@Composable
private fun TopBarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(dimen(R.dimen.size_40))
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = EduTheme.colors.ink,
            modifier = Modifier.size(dimen(R.dimen.size_22)),
        )
    }
}

/** First name for greetings, skipping academic titles such as "Dr." or "Prof.". */
private fun displayFirstName(fullName: String, fallback: String): String {
    val titles = setOf("dr.", "dr", "prof.", "prof", "drs.", "drs", "ir.", "ir", "h.", "hj.")
    val name = fullName.trim().split(" ")
        .firstOrNull { it.isNotBlank() && it.lowercase() !in titles }
        ?.trimEnd(',')
    return name?.takeIf { it.isNotBlank() } ?: fallback
}

@Composable
private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 11 -> stringResource(R.string.home_time_morning)
        hour < 15 -> stringResource(R.string.home_time_afternoon)
        hour < 19 -> stringResource(R.string.home_time_evening)
        else -> stringResource(R.string.home_time_night)
    }
}
