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
import com.eduvark.app.ui.theme.EduColors
import com.eduvark.app.ui.theme.EduText
import kotlinx.coroutines.delay

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

    EduScreen(
        title = "Manajemen akun",
        onBack = onBack,
        modifier = modifier,
        bottomBar = bottomBar,
    ) { padding ->
        EduPullToRefresh(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding),
        ) {
        ScrollColumn(spacing = 0.dp) {
            if (state.refreshError != null) {
                NoticeCard(text = state.refreshError.orEmpty(), tone = TintTone.Danger)
                Spacer(Modifier.height(16.dp))
            }
            Eyebrow("${state.total} akun terdaftar")
            Spacer(Modifier.height(10.dp))
            Text(text = "Kelola akses pengguna.", style = EduText.Display)
            Spacer(Modifier.height(10.dp))
            Text(text = "Mahasiswa, dosen, dan admin", style = EduText.Body)

            Spacer(Modifier.height(22.dp))
            FieldLabel("Cari akun")
            EduField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Nama atau identitas login…",
            )

            Spacer(Modifier.height(16.dp))
            FieldLabel("Filter")
            EduDropdown(
                value = activeFilter.label,
                options = AdminUsersFilters.map { it.label },
                onSelect = { label ->
                    val picked = AdminUsersFilters.firstOrNull { it.label == label }
                    if (picked != null) {
                        if (picked.role != state.roleFilter) viewModel.setRoleFilter(picked.role)
                        if (picked.status != state.statusFilter) {
                            viewModel.setStatusFilter(picked.status)
                        }
                    }
                },
            )

            Spacer(Modifier.height(18.dp))
            PrimaryButton(text = "+ Buat akun", onClick = onCreateUser)

            Spacer(Modifier.height(20.dp))
            val error = state.error
            when {
                state.isLoading && state.users.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
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
                            "Tidak ada akun yang cocok dengan pencarian atau filter ini."
                        } else {
                            "Belum ada akun terdaftar."
                        },
                        tone = TintTone.Neutral,
                    )
                }

                else -> {
                    if (error != null) {
                        NoticeCard(text = error, tone = TintTone.Danger)
                        Spacer(Modifier.height(12.dp))
                    }
                    state.users.forEachIndexed { index, user ->
                        if (index > 0) Spacer(Modifier.height(12.dp))
                        UserCard(
                            name = user.fullName,
                            meta = "${user.loginId} • ${user.role.label}",
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
                        Spacer(Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PageLink(
                                text = "Sebelumnya",
                                icon = EduIcons.ChevronLeft,
                                iconAfter = false,
                                enabled = state.page > 1 && !state.isLoading,
                                onClick = viewModel::previousPage,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "Halaman ${state.page} dari $pageCount",
                                style = EduText.BodySmall,
                            )
                            Spacer(Modifier.weight(1f))
                            PageLink(
                                text = "Berikutnya",
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
) {
    val label: String
        get() = (role?.label ?: "Semua role") + " • " + (status?.label ?: "Semua status")
}

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
    val color = if (enabled) EduColors.Teal else EduColors.MutedSoft
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!iconAfter) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(text = text, style = EduText.Link.copy(color = color))
        if (iconAfter) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
