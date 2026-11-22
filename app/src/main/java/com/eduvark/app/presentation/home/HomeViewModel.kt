package com.eduvark.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.AdminAccountStatus
import com.eduvark.app.domain.model.AdminUser
import com.eduvark.app.domain.model.Topic
import com.eduvark.app.domain.model.TopicStatus
import com.eduvark.app.domain.model.UserProfile
import com.eduvark.app.domain.model.UserRole
import com.eduvark.app.domain.usecase.GetMeUseCase
import com.eduvark.app.domain.usecase.GetTopicsUseCase
import com.eduvark.app.domain.usecase.ListAdminUsersUseCase
import com.eduvark.app.domain.usecase.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminSummary(
    val total: Int,
    val active: Int,
    val inactive: Int,
    val students: Int,
    val lecturers: Int,
    val admins: Int,
) {
    companion object {
        fun from(users: List<AdminUser>, fallbackTotal: Int): AdminSummary {
            val students = users.count { it.role == UserRole.STUDENT }
            val lecturers = users.count { it.role == UserRole.LECTURER }
            val admins = users.count { it.role == UserRole.ADMIN }
            return AdminSummary(
                total = maxOf(fallbackTotal, users.size),
                active = users.count { it.status == AdminAccountStatus.ACTIVE },
                inactive = users.count { it.status == AdminAccountStatus.INACTIVE },
                students = students,
                lecturers = lecturers,
                admins = admins,
            )
        }
    }
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val user: UserProfile? = null,
    val topics: List<Topic> = emptyList(),
    val adminUsers: List<AdminUser> = emptyList(),
    val adminSummary: AdminSummary? = null,
    val error: String? = null,
    val loggedOut: Boolean = false,
) {
    val publishedTopics: List<Topic>
        get() = topics.filter { it.status == TopicStatus.PUBLISHED }

    val draftTopics: List<Topic>
        get() = topics.filter { it.status == TopicStatus.DRAFT }

    val readyTopics: List<Topic>
        get() = topics.filter { it.materialAvailable }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getMeUseCase: GetMeUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val getTopicsUseCase: GetTopicsUseCase,
    private val listAdminUsersUseCase: ListAdminUsersUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, loggedOut = false) }
            try {
                val user = getMeUseCase()
                _state.update { it.copy(user = user, isLoading = false) }
                loadDashboardData(user)
            } catch (error: AppFailure) {
                if (error is AppFailure.Api && error.statusCode == 401) {
                    _state.update { it.copy(isLoading = false, loggedOut = true) }
                } else {
                    _state.update { it.copy(isLoading = false, error = error.message) }
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = "Terjadi kesalahan. Coba lagi.")
                }
            }
        }
    }

    /**
     * Dashboard counters are a nice-to-have: a failure here must not block the
     * home screen, so each call is isolated.
     */
    private suspend fun loadDashboardData(user: UserProfile) = coroutineScope {
        if (user.role == UserRole.ADMIN) {
            async {
                runCatching {
                    listAdminUsersUseCase(
                        query = null,
                        role = null,
                        status = null,
                        page = 1,
                        pageSize = 100,
                    )
                }.onSuccess { page ->
                    _state.update {
                        it.copy(
                            adminSummary = AdminSummary.from(page.items, page.total),
                            adminUsers = page.items.take(4),
                            topics = emptyList(),
                        )
                    }
                }
            }
        } else {
            async {
                runCatching { getTopicsUseCase() }.onSuccess { topics ->
                    _state.update { it.copy(topics = topics) }
                }
            }
        }
        Unit
    }

    fun logout() {
        viewModelScope.launch {
            try {
                logoutUseCase()
            } catch (_: Exception) {
                // Session is cleared locally by the repository in its finally block.
            } finally {
                _state.update { it.copy(loggedOut = true) }
            }
        }
    }

    fun consumeLoggedOut() {
        _state.update { it.copy(loggedOut = false) }
    }
}
