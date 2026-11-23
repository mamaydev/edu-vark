package com.eduvark.app.core.network

import com.eduvark.app.data.remote.dto.AdminUserListResponse
import com.eduvark.app.data.remote.dto.AdminUserResponse
import com.eduvark.app.data.remote.dto.AssessmentResponse
import com.eduvark.app.data.remote.dto.CaseResponse
import com.eduvark.app.data.remote.dto.CaseSubmissionListResponse
import com.eduvark.app.data.remote.dto.ChangePasswordRequest
import com.eduvark.app.data.remote.dto.CreateAdminUserRequest
import com.eduvark.app.data.remote.dto.CreateAssessmentRequest
import com.eduvark.app.data.remote.dto.CreateTopicRequest
import com.eduvark.app.data.remote.dto.EnrollmentResponse
import com.eduvark.app.data.remote.dto.EnrollmentStatusRequest
import com.eduvark.app.data.remote.dto.FeedbackResponse
import com.eduvark.app.data.remote.dto.HealthResponse
import com.eduvark.app.data.remote.dto.LinkMaterialRequest
import com.eduvark.app.data.remote.dto.LoginRequest
import com.eduvark.app.data.remote.dto.LoginResponse
import com.eduvark.app.data.remote.dto.MaterialListResponse
import com.eduvark.app.data.remote.dto.MaterialResponse
import com.eduvark.app.data.remote.dto.MeResponse
import com.eduvark.app.data.remote.dto.RefreshRequest
import com.eduvark.app.data.remote.dto.RefreshResponse
import com.eduvark.app.data.remote.dto.ReorderTopicsRequest
import com.eduvark.app.data.remote.dto.ResetPasswordRequest
import com.eduvark.app.data.remote.dto.SaveFeedbackRequest
import com.eduvark.app.data.remote.dto.SubmissionDetailResponse
import com.eduvark.app.data.remote.dto.SubmitCaseRequest
import com.eduvark.app.data.remote.dto.SubmissionResponse
import com.eduvark.app.data.remote.dto.StudentDashboardResponse
import com.eduvark.app.data.remote.dto.TopicAnalyticsResponse
import com.eduvark.app.data.remote.dto.TopicListResponse
import com.eduvark.app.data.remote.dto.TopicResponse
import com.eduvark.app.data.remote.dto.UpdateAdminUserRequest
import com.eduvark.app.data.remote.dto.UpdateMaterialRequest
import com.eduvark.app.data.remote.dto.UpdateTopicRequest
import com.eduvark.app.data.remote.dto.UpsertCaseRequest
import com.eduvark.app.data.remote.dto.VarkRequest
import com.eduvark.app.data.remote.dto.VarkResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface EduVarkApi {
    @GET("health")
    suspend fun health(): HealthResponse

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("auth/me")
    suspend fun me(): MeResponse

    @POST("auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<Unit>

    @POST("auth/vark")
    suspend fun selectVark(@Body request: VarkRequest): VarkResponse

    @GET("topics")
    suspend fun listTopics(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 50,
    ): TopicListResponse

    @GET("topics/{id}")
    suspend fun getTopic(@Path("id") topicId: String): TopicResponse

    @GET("student/dashboard")
    suspend fun getStudentDashboard(): StudentDashboardResponse

    @GET("topics/{id}/materials")
    suspend fun listMaterials(@Path("id") topicId: String): MaterialListResponse

    @GET("topics/{id}/feedback")
    suspend fun getFeedback(@Path("id") topicId: String): FeedbackResponse

    @PUT("topics/{id}/feedback")
    suspend fun saveFeedback(
        @Path("id") topicId: String,
        @Body request: SaveFeedbackRequest,
    ): FeedbackResponse

    @GET("topics/{id}/case")
    suspend fun getStudentCase(@Path("id") topicId: String): CaseResponse

    @POST("topics/{id}/case/submission")
    suspend fun submitCase(
        @Path("id") topicId: String,
        @Body request: SubmitCaseRequest,
    ): SubmissionResponse

    @GET("lecturer/topics/{id}/case")
    suspend fun getLecturerCase(@Path("id") topicId: String): CaseResponse

    @PUT("lecturer/topics/{id}/case")
    suspend fun upsertCase(
        @Path("id") topicId: String,
        @Body request: UpsertCaseRequest,
    ): CaseResponse

    @GET("lecturer/cases/{id}/submissions")
    suspend fun listCaseSubmissions(
        @Path("id") caseId: String,
    ): CaseSubmissionListResponse

    @GET("submissions/{id}")
    suspend fun getSubmission(@Path("id") submissionId: String): SubmissionDetailResponse

    @POST("submissions/{id}/assessment")
    suspend fun createAssessment(
        @Path("id") submissionId: String,
        @Body request: CreateAssessmentRequest,
    ): AssessmentResponse

    @GET("lecturer/analytics/topics/{id}")
    suspend fun getTopicAnalytics(
        @Path("id") topicId: String,
        @Query("category") category: String? = null,
        @Query("studentId") studentId: String? = null,
    ): TopicAnalyticsResponse

    @GET("admin/users")
    suspend fun listAdminUsers(
        @Query("q") query: String? = null,
        @Query("role") role: String? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): AdminUserListResponse

    @POST("admin/users")
    suspend fun createAdminUser(
        @Body request: CreateAdminUserRequest,
    ): AdminUserResponse

    @GET("admin/users/{id}")
    suspend fun getAdminUser(@Path("id") userId: String): AdminUserResponse

    @PATCH("admin/users/{id}")
    suspend fun updateAdminUser(
        @Path("id") userId: String,
        @Body request: UpdateAdminUserRequest,
    ): AdminUserResponse

    @POST("admin/users/{id}/reset-password")
    suspend fun resetAdminPassword(
        @Path("id") userId: String,
        @Body request: ResetPasswordRequest,
    ): Response<Unit>

    @POST("admin/users/{id}/deactivate")
    suspend fun deactivateAdminUser(@Path("id") userId: String): Response<Unit>

    @PATCH("admin/enrollments/{id}")
    suspend fun updateEnrollment(
        @Path("id") enrollmentId: String,
        @Body request: EnrollmentStatusRequest,
    ): EnrollmentResponse

    @POST("lecturer/topics")
    suspend fun createTopic(@Body request: CreateTopicRequest): TopicResponse

    @PATCH("lecturer/topics/{id}")
    suspend fun updateTopic(
        @Path("id") topicId: String,
        @Body request: UpdateTopicRequest,
    ): TopicResponse

    @POST("lecturer/topics/{id}/publish")
    suspend fun publishTopic(@Path("id") topicId: String): TopicResponse

    @POST("lecturer/topics/{id}/unpublish")
    suspend fun unpublishTopic(@Path("id") topicId: String): TopicResponse

    @DELETE("lecturer/topics/{id}")
    suspend fun deleteTopic(@Path("id") topicId: String): Response<Unit>

    @PATCH("lecturer/topics/reorder")
    suspend fun reorderTopics(@Body request: ReorderTopicsRequest): Response<Unit>

    @GET("lecturer/topics/{id}/materials")
    suspend fun listLecturerMaterials(@Path("id") topicId: String): MaterialListResponse

    @POST("lecturer/topics/{id}/materials/link")
    suspend fun createLinkMaterial(
        @Path("id") topicId: String,
        @Body request: LinkMaterialRequest,
    ): MaterialResponse

    @Multipart
    @POST("lecturer/topics/{id}/materials/file")
    suspend fun uploadFileMaterial(
        @Path("id") topicId: String,
        @Part file: MultipartBody.Part,
        @Part("title") title: RequestBody,
        @Part("vark") vark: RequestBody,
    ): MaterialResponse

    @PATCH("lecturer/materials/{id}")
    suspend fun updateMaterial(
        @Path("id") materialId: String,
        @Body request: UpdateMaterialRequest,
    ): MaterialResponse

    @DELETE("lecturer/materials/{id}")
    suspend fun deleteMaterial(@Path("id") materialId: String): Response<Unit>
}

interface RefreshApi {
    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): RefreshResponse
}
