package com.eduvark.app.presentation.admin.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.AdminAccountStatus
import com.eduvark.app.domain.model.AdminUser
import com.eduvark.app.domain.model.UserRole
import com.eduvark.app.domain.usecase.ListAdminUsersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUsersUiState(
    val isLoading: Boolean = true,
    val users: List<AdminUser> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
    val query: String = "",
    val roleFilter: UserRole? = null,
    val statusFilter: AdminAccountStatus? = null,
    val error: String? = null,
)

@HiltViewModel
class AdminUsersViewModel @Inject constructor(
    private val listAdminUsersUseCase: ListAdminUsersUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminUsersUiState())
    val state: StateFlow<AdminUsersUiState> = _state.asStateFlow()

    fun load(page: Int = _state.value.page) {
        viewModelScope.launch {
            val current = _state.value
            _state.update { it.copy(isLoading = true, error = null, page = page) }
            try {
                val result = listAdminUsersUseCase(
                    query = current.query.trim().ifBlank { null },
                    role = current.roleFilter,
                    status = current.statusFilter,
                    page = page,
                    pageSize = current.pageSize,
                )
                _state.update {
                    it.copy(
                        isLoading = false,
                        users = result.items,
                        total = result.total,
                        page = result.page,
                        pageSize = result.pageSize,
                    )
                }
            } catch (error: AppFailure) {
                _state.update { it.copy(isLoading = false, error = error.message) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = "Gagal memuat pengguna.")
                }
            }
        }
    }

    fun search(query: String) {
        _state.update { it.copy(query = query, page = 1) }
        load(1)
    }

    fun setRoleFilter(role: UserRole?) {
        _state.update { it.copy(roleFilter = role, page = 1) }
        load(1)
    }

    fun setStatusFilter(status: AdminAccountStatus?) {
        _state.update { it.copy(statusFilter = status, page = 1) }
        load(1)
    }

    fun nextPage() {
        val current = _state.value
        if (current.page * current.pageSize < current.total) load(current.page + 1)
    }

    fun previousPage() {
        val current = _state.value
        if (current.page > 1) load(current.page - 1)
    }
}
