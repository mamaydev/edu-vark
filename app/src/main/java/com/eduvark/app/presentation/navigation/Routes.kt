package com.eduvark.app.presentation.navigation

object Routes {
    const val LOGIN = "login"
    const val SERVER_SETTINGS = "server-settings"
    const val CHANGE_PASSWORD = "change-password"
    const val CHANGE_PASSWORD_SELF = "account/change-password"
    const val VARK = "vark"
    const val HOME = "home"
    const val ACCOUNT = "account"

    const val STUDENT_TOPICS = "student/topics"
    const val STUDENT_MATERIALS = "student/topics/{topicId}/materials"
    const val STUDENT_CASE = "student/topics/{topicId}/case"

    const val LECTURER_TOPICS = "lecturer/topics"
    const val LECTURER_GRADING = "lecturer/grading"
    const val LECTURER_ANALYTICS_HUB = "lecturer/analytics"
    const val LECTURER_MATERIALS = "lecturer/topics/{topicId}/materials"
    const val LECTURER_CASE = "lecturer/topics/{topicId}/case"
    const val LECTURER_SUBMISSIONS = "lecturer/cases/{caseId}/submissions"
    const val LECTURER_GRADE = "lecturer/submissions/{submissionId}/assessment"
    const val LECTURER_ANALYTICS = "lecturer/analytics/{topicId}"

    const val ADMIN_USERS = "admin/users"
    const val ADMIN_CREATE_USER = "admin/users/new"
    const val ADMIN_USER_DETAIL = "admin/users/{userId}"

    fun studentMaterials(topicId: String): String = "student/topics/$topicId/materials"
    fun studentCase(topicId: String): String = "student/topics/$topicId/case"
    fun lecturerMaterials(topicId: String): String = "lecturer/topics/$topicId/materials"
    fun lecturerCase(topicId: String): String = "lecturer/topics/$topicId/case"
    fun lecturerSubmissions(caseId: String): String = "lecturer/cases/$caseId/submissions"
    fun lecturerGrade(submissionId: String): String =
        "lecturer/submissions/$submissionId/assessment"

    fun lecturerAnalytics(topicId: String): String = "lecturer/analytics/$topicId"
    fun adminUserDetail(userId: String): String = "admin/users/$userId"
}
