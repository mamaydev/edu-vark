package com.eduvark.app.presentation.admin.users

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduvark.app.domain.model.AdminAccountStatus
import com.eduvark.app.domain.model.UserRole
import com.eduvark.app.ui.components.EduDropdown
import com.eduvark.app.ui.components.EduField
import com.eduvark.app.ui.components.EduIcons
import com.eduvark.app.ui.components.EduPullToRefresh
import com.eduvark.app.ui.components.EduScreen
import com.eduvark.app.ui.components.ErrorBlock
import com.eduvark.app.ui.components.Eyebrow
import com.eduvark.app.ui.components.FieldLabel
import com.eduvark.app.ui.components.LoadingBlock
import com.eduvark.app.ui.components.NoticeCard
import com.eduvark.app.ui.components.PrimaryButton
import com.eduvark.app.ui.components.ScrollColumn
import com.eduvark.app.ui.components.TintTone
import com.eduvark.app.ui.components.UserCard
import kotlinx.coroutines.delay
import com.eduvark.app.ui.theme.EduTheme
import com.eduvark.app.R
import com.eduvark.app.ui.theme.dimen

/** Debounce applied while typing in the search field before asking the ViewModel. */
private const val SEARCH_DEBOUNCE_MS = 400L

@Composable
fun AdminUsersScreen(
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    onCreateUser: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    viewModel: AdminUsersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }

    LaunchedEffect(query) {
        if (query == state.query) return@LaunchedEffect
        delay(SEARCH_DEBOUNCE_MS)
        if (query != state.query) viewModel.search(query)
    }

    val activeFilter = AdminUsersFilter(role = state.roleFilter, status = state.statusFilter)
    val pageCount = if (state.pageSize <= 0) {
        1
    } else {
        (state.total + state.pageSize - 1) / state.pageSize
    }
    val hasFilter = query.isNotBlank() ||
        state.query.isNotBlank() ||
        state.roleFilter != null ||
        state.statusFilter != null
    val allRolesLabel = stringResource(R.string.admin_filter_all_roles)
    val allStatusLabel = stringResource(R.string.admin_filter_all_status)
    val filterFormat = stringResource(R.string.admin_filter_label)
    val activeFilterLabel = filterLabel(activeFilter, allRolesLabel, allStatusLabel, filterFormat)

    EduScreen(
        title = stringResource(R.string.admin_users_title),
        onBack = onBack,
        modifier = modifier,
        bottomBar = bottomBar,
    ) { padding ->
        EduPullToRefresh(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding),
        ) {
        ScrollColumn(spacing = dimen(R.dimen.space_0)) {
            if (state.refreshError != null) {
                NoticeCard(text = state.refreshError.orEmpty(), tone = TintTone.Danger)
                Spacer(Modifier.height(dimen(R.dimen.space_16)))
            }
            Eyebrow(stringResource(R.string.admin_users_registered_count, state.total))
            Spacer(Modifier.height(dimen(R.dimen.space_10)))
            Text(text = stringResource(R.string.admin_users_headline), style = EduTheme.type.display)
            Spacer(Modifier.height(dimen(R.dimen.space_10)))
            Text(text = stringResource(R.string.admin_users_subtitle), style = EduTheme.type.body)

            Spacer(Modifier.height(dimen(R.dimen.space_22)))
            FieldLabel(stringResource(R.string.admin_users_search_label))
            EduField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.admin_users_search_placeholder),
            )

            Spacer(Modifier.height(dimen(R.dimen.space_16)))
            FieldLabel(stringResource(R.string.admin_users_filter_label))
            EduDropdown(
                value = activeFilterLabel,
                options = AdminUsersFilters.map {
                    filterLabel(it, allRolesLabel, allStatusLabel, filterFormat)
                },
                onSelect = { label ->
                    val picked = AdminUsersFilters.firstOrNull {
                        filterLabel(it, allRolesLabel, allStatusLabel, filterFormat) == label
                    }
                    if (picked != null) {
                        if (picked.role != state.roleFilter) viewModel.setRoleFilter(picked.role)
                        if (picked.status != state.statusFilter) {
                            viewModel.setStatusFilter(picked.status)
                        }
                    }
                },
            )

            Spacer(Modifier.height(dimen(R.dimen.space_18)))
            PrimaryButton(text = stringResource(R.string.admin_users_create_button), onClick = onCreateUser)

            Spacer(Modifier.height(dimen(R.dimen.space_20)))
            val error = state.error
            when {
                state.isLoading && state.users.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dimen(R.dimen.size_180)),
                    ) {
                        LoadingBlock()
                    }
                }

                error != null && state.users.isEmpty() -> {
                    ErrorBlock(
                        message = error,
                        onRetry = { viewModel.load() },
                    )
                }

                state.users.isEmpty() -> {
                    NoticeCard(
                        text = if (hasFilter) {
                            stringResource(R.string.admin_users_empty_filtered)
                        } else {
                            stringResource(R.string.admin_users_empty)
                        },
                        tone = TintTone.Neutral,
                    )
                }

                else -> {
                    if (error != null) {
                        NoticeCard(text = error, tone = TintTone.Danger)
                        Spacer(Modifier.height(dimen(R.dimen.space_12)))
                    }
                    state.users.forEachIndexed { index, user ->
                        if (index > 0) Spacer(Modifier.height(dimen(R.dimen.space_12)))
                        UserCard(
                            name = user.fullName,
                            meta = stringResource(
                                R.string.admin_user_meta,
                                user.loginId,
                                user.role.label,
                            ),
                            status = user.status.label,
                            statusTone = if (user.status == AdminAccountStatus.ACTIVE) {
                                TintTone.Teal
                            } else {
                                TintTone.Danger
                            },
                            onClick = { onOpenUser(user.id) },
                        )
                    }

                    if (pageCount > 1) {
                        Spacer(Modifier.height(dimen(R.dimen.space_18)))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PageLink(
                                text = stringResource(R.string.admin_users_prev_page),
                                icon = EduIcons.ChevronLeft,
                                iconAfter = false,
                                enabled = state.page > 1 && !state.isLoading,
                                onClick = viewModel::previousPage,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = stringResource(
                                    R.string.admin_users_page_of,
                                    state.page,
                                    pageCount,
                                ),
                                style = EduTheme.type.bodySmall,
                            )
                            Spacer(Modifier.weight(1f))
                            PageLink(
                                text = stringResource(R.string.admin_users_next_page),
                                icon = EduIcons.ChevronRight,
                                iconAfter = true,
                                enabled = state.page < pageCount && !state.isLoading,
                                onClick = viewModel::nextPage,
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

/** One selectable combination of the role and status filters. */
private data class AdminUsersFilter(
    val role: UserRole?,
    val status: AdminAccountStatus?,
)

/**
 * Localised dropdown label of a filter combination. The caller resolves the
 * literal labels from resources because this helper runs outside composition.
 */
private fun filterLabel(
    filter: AdminUsersFilter,
    allRoles: String,
    allStatus: String,
    format: String,
): String = format.format(
    filter.role?.label ?: allRoles,
    filter.status?.label ?: allStatus,
)

private val AdminUsersFilters: List<AdminUsersFilter> = buildList {
    add(AdminUsersFilter(role = null, status = null))
    UserRole.entries.forEach { role ->
        add(AdminUsersFilter(role = role, status = null))
        AdminAccountStatus.entries.forEach { status ->
            add(AdminUsersFilter(role = role, status = status))
        }
    }
    AdminAccountStatus.entries.forEach { status ->
        add(AdminUsersFilter(role = null, status = status))
    }
}

@Composable
private fun PageLink(
    text: String,
    icon: ImageVector,
    iconAfter: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val color = if (enabled) EduTheme.colors.lavenderDim else EduTheme.colors.mutedSoft
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(dimen(R.dimen.radius_8)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = dimen(R.dimen.radius_4), vertical = dimen(R.dimen.radius_6)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!iconAfter) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(dimen(R.dimen.size_16)),
            )
            Spacer(Modifier.width(dimen(R.dimen.space_4)))
        }
        Text(text = text, style = EduTheme.type.link.copy(color = color))
        if (iconAfter) {
            Spacer(Modifier.width(dimen(R.dimen.space_4)))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(dimen(R.dimen.size_16)),
            )
        }
    }
}
