package com.eduvark.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eduvark.app.presentation.account.AccountScreen
import com.eduvark.app.presentation.admin.users.AdminCreateUserScreen
import com.eduvark.app.presentation.admin.users.AdminUserDetailScreen
import com.eduvark.app.presentation.admin.users.AdminUsersScreen
import com.eduvark.app.presentation.auth.ChangePasswordScreen
import com.eduvark.app.presentation.auth.LoginScreen
import com.eduvark.app.presentation.auth.ServerSettingsScreen
import com.eduvark.app.presentation.home.HomeScreen
import com.eduvark.app.presentation.lecturer.analytics.LecturerAnalyticsScreen
import com.eduvark.app.presentation.lecturer.assessments.LecturerGradeScreen
import com.eduvark.app.presentation.lecturer.assessments.LecturerSubmissionsScreen
import com.eduvark.app.presentation.lecturer.cases.LecturerCaseScreen
import com.eduvark.app.presentation.lecturer.hub.LecturerHubMode
import com.eduvark.app.presentation.lecturer.hub.LecturerHubScreen
import com.eduvark.app.presentation.lecturer.materials.LecturerMaterialsScreen
import com.eduvark.app.presentation.lecturer.topics.LecturerTopicsScreen
import com.eduvark.app.presentation.student.cases.StudentCaseScreen
import com.eduvark.app.presentation.student.materials.StudentMaterialsScreen
import com.eduvark.app.presentation.student.topics.StudentTopicsScreen
import com.eduvark.app.presentation.vark.VarkSelectionScreen
import com.eduvark.app.ui.components.AdminBottomBar
import com.eduvark.app.ui.components.AdminTab
import com.eduvark.app.ui.components.LecturerBottomBar
import com.eduvark.app.ui.components.LecturerTab
import com.eduvark.app.ui.components.StudentBottomBar
import com.eduvark.app.ui.components.StudentTab

@Composable
fun EduVarkApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.LOGIN,
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onOpenServerSettings = { navController.navigate(Routes.SERVER_SETTINGS) },
                onLoginSuccess = { mustChangePassword, varkRequired ->
                    val destination = when {
                        mustChangePassword -> Routes.CHANGE_PASSWORD
                        varkRequired -> Routes.VARK
                        else -> Routes.HOME
                    }
                    navController.navigate(destination) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Routes.SERVER_SETTINGS) {
            ServerSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.CHANGE_PASSWORD) {
            ChangePasswordScreen(
                onBack = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onSuccess = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Routes.VARK) {
            VarkSelectionScreen(
                onSelected = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.VARK) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBack = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onOpenStudentTopics = { navController.openRoot(Routes.STUDENT_TOPICS) },
                onOpenLecturerTopics = { navController.openRoot(Routes.LECTURER_TOPICS) },
                onOpenAccount = { navController.openRoot(Routes.ACCOUNT) },
                onOpenGrading = { navController.openRoot(Routes.LECTURER_GRADING) },
                onOpenAnalytics = { navController.openRoot(Routes.LECTURER_ANALYTICS_HUB) },
                onCreateTopic = { navController.openRoot(Routes.LECTURER_TOPICS) },
                onCreateUser = { navController.navigate(Routes.ADMIN_CREATE_USER) },
                onOpenAdminAccounts = { navController.openRoot(Routes.ADMIN_USERS) },
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Routes.ACCOUNT) {
            AccountScreen(
                onBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onOpenHome = { navController.openRoot(Routes.HOME) },
                onOpenStudentTopics = { navController.openRoot(Routes.STUDENT_TOPICS) },
                onOpenLecturerTopics = { navController.openRoot(Routes.LECTURER_TOPICS) },
                onOpenGrading = { navController.openRoot(Routes.LECTURER_GRADING) },
                onOpenAnalytics = { navController.openRoot(Routes.LECTURER_ANALYTICS_HUB) },
                onOpenAdminAccounts = { navController.openRoot(Routes.ADMIN_USERS) },
                onOpenServerSettings = { navController.navigate(Routes.SERVER_SETTINGS) },
            )
        }

        composable(Routes.STUDENT_TOPICS) {
            StudentTopicsScreen(
                onBack = { navController.popBackStack() },
                onOpenTopic = { topicId, isKinesthetic ->
                    navController.navigate(
                        if (isKinesthetic) Routes.studentCase(topicId)
                        else Routes.studentMaterials(topicId),
                    )
                },
                bottomBar = { StudentShellBar(navController, StudentTab.TOPIK) },
            )
        }

        composable(
            route = Routes.STUDENT_MATERIALS,
            arguments = listOf(navArgument("topicId") { type = NavType.StringType }),
        ) {
            StudentMaterialsScreen(
                onBack = { navController.popBackStack() },
                onOpenTopics = { navController.openRoot(Routes.STUDENT_TOPICS) },
                bottomBar = { StudentShellBar(navController, StudentTab.TOPIK) },
            )
        }

        composable(
            route = Routes.STUDENT_CASE,
            arguments = listOf(navArgument("topicId") { type = NavType.StringType }),
        ) {
            StudentCaseScreen(
                onBack = { navController.popBackStack() },
                onOpenTopics = { navController.openRoot(Routes.STUDENT_TOPICS) },
                onOpenHome = { navController.openRoot(Routes.HOME) },
                bottomBar = { StudentShellBar(navController, StudentTab.TOPIK) },
            )
        }

        composable(Routes.LECTURER_TOPICS) {
            LecturerTopicsScreen(
                onBack = { navController.popBackStack() },
                onOpenMaterials = { topicId ->
                    navController.navigate(Routes.lecturerMaterials(topicId))
                },
                onOpenCase = { topicId -> navController.navigate(Routes.lecturerCase(topicId)) },
                onOpenAnalytics = { topicId ->
                    navController.navigate(Routes.lecturerAnalytics(topicId))
                },
                bottomBar = { LecturerShellBar(navController, LecturerTab.TOPIK) },
            )
        }

        composable(Routes.LECTURER_GRADING) {
            LecturerHubScreen(
                mode = LecturerHubMode.GRADING,
                onBack = { navController.popBackStack() },
                onOpenSubmissions = { caseId ->
                    navController.navigate(Routes.lecturerSubmissions(caseId))
                },
                bottomBar = { LecturerShellBar(navController, LecturerTab.PENILAIAN) },
            )
        }

        composable(Routes.LECTURER_ANALYTICS_HUB) {
            LecturerHubScreen(
                mode = LecturerHubMode.ANALYTICS,
                onBack = { navController.popBackStack() },
                onOpenAnalytics = { topicId ->
                    navController.navigate(Routes.lecturerAnalytics(topicId))
                },
                bottomBar = { LecturerShellBar(navController, LecturerTab.ANALITIK) },
            )
        }

        composable(
            route = Routes.LECTURER_MATERIALS,
            arguments = listOf(navArgument("topicId") { type = NavType.StringType }),
        ) {
            LecturerMaterialsScreen(
                onBack = { navController.popBackStack() },
                bottomBar = { LecturerShellBar(navController, LecturerTab.TOPIK) },
            )
        }

        composable(
            route = Routes.LECTURER_CASE,
            arguments = listOf(navArgument("topicId") { type = NavType.StringType }),
        ) {
            LecturerCaseScreen(
                onBack = { navController.popBackStack() },
                onOpenSubmissions = { caseId ->
                    navController.navigate(Routes.lecturerSubmissions(caseId))
                },
                bottomBar = { LecturerShellBar(navController, LecturerTab.PENILAIAN) },
            )
        }

        composable(
            route = Routes.LECTURER_SUBMISSIONS,
            arguments = listOf(navArgument("caseId") { type = NavType.StringType }),
        ) {
            LecturerSubmissionsScreen(
                onBack = { navController.popBackStack() },
                onOpenSubmission = { submissionId ->
                    navController.navigate(Routes.lecturerGrade(submissionId))
                },
                bottomBar = { LecturerShellBar(navController, LecturerTab.PENILAIAN) },
            )
        }

        composable(
            route = Routes.LECTURER_GRADE,
            arguments = listOf(navArgument("submissionId") { type = NavType.StringType }),
        ) {
            LecturerGradeScreen(
                onBack = { navController.popBackStack() },
                bottomBar = { LecturerShellBar(navController, LecturerTab.PENILAIAN) },
            )
        }

        composable(
            route = Routes.LECTURER_ANALYTICS,
            arguments = listOf(navArgument("topicId") { type = NavType.StringType }),
        ) {
            LecturerAnalyticsScreen(
                onBack = { navController.popBackStack() },
                bottomBar = { LecturerShellBar(navController, LecturerTab.ANALITIK) },
            )
        }

        composable(Routes.ADMIN_USERS) {
            AdminUsersScreen(
                onBack = { navController.popBackStack() },
                onOpenUser = { userId -> navController.navigate(Routes.adminUserDetail(userId)) },
                onCreateUser = { navController.navigate(Routes.ADMIN_CREATE_USER) },
                bottomBar = { AdminShellBar(navController, AdminTab.AKUN) },
            )
        }

        composable(Routes.ADMIN_CREATE_USER) {
            AdminCreateUserScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() },
                bottomBar = { AdminShellBar(navController, AdminTab.AKUN) },
            )
        }

        composable(
            route = Routes.ADMIN_USER_DETAIL,
            arguments = listOf(navArgument("userId") { type = NavType.StringType }),
        ) {
            AdminUserDetailScreen(
                onBack = { navController.popBackStack() },
                bottomBar = { AdminShellBar(navController, AdminTab.AKUN) },
            )
        }
    }
}

/**
 * Switches between the shell roots without stacking duplicates: every root tab
 * clears anything opened above the home destination.
 */
private fun NavHostController.openRoot(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { inclusive = route == Routes.HOME }
        launchSingleTop = true
    }
}

@Composable
private fun StudentShellBar(navController: NavHostController, selected: StudentTab) {
    StudentBottomBar(selected) { tab ->
        when (tab) {
            StudentTab.BERANDA -> navController.openRoot(Routes.HOME)
            StudentTab.TOPIK -> navController.openRoot(Routes.STUDENT_TOPICS)
            StudentTab.AKUN -> navController.openRoot(Routes.ACCOUNT)
        }
    }
}

@Composable
private fun LecturerShellBar(navController: NavHostController, selected: LecturerTab) {
    LecturerBottomBar(selected) { tab ->
        when (tab) {
            LecturerTab.BERANDA -> navController.openRoot(Routes.HOME)
            LecturerTab.TOPIK -> navController.openRoot(Routes.LECTURER_TOPICS)
            LecturerTab.PENILAIAN -> navController.openRoot(Routes.LECTURER_GRADING)
            LecturerTab.ANALITIK -> navController.openRoot(Routes.LECTURER_ANALYTICS_HUB)
            LecturerTab.AKUN -> navController.openRoot(Routes.ACCOUNT)
        }
    }
}

@Composable
private fun AdminShellBar(navController: NavHostController, selected: AdminTab) {
    AdminBottomBar(selected) { tab ->
        when (tab) {
            AdminTab.BERANDA -> navController.openRoot(Routes.HOME)
            AdminTab.AKUN -> navController.openRoot(Routes.ADMIN_USERS)
            AdminTab.PROFIL -> navController.openRoot(Routes.ACCOUNT)
        }
    }
}
