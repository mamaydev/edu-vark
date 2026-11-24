package com.eduvark.app.core.di

import com.eduvark.app.data.repository.AuthRepositoryImpl
import com.eduvark.app.data.repository.AdminRepositoryImpl
import com.eduvark.app.data.repository.AnalyticsRepositoryImpl
import com.eduvark.app.data.repository.AssessmentRepositoryImpl
import com.eduvark.app.data.repository.CaseRepositoryImpl
import com.eduvark.app.data.repository.FeedbackRepositoryImpl
import com.eduvark.app.data.repository.HealthRepositoryImpl
import com.eduvark.app.data.repository.TopicRepositoryImpl
import com.eduvark.app.core.files.AndroidMaterialFileStore
import com.eduvark.app.core.files.HttpMaterialContentSource
import com.eduvark.app.domain.repository.AdminRepository
import com.eduvark.app.domain.repository.AnalyticsRepository
import com.eduvark.app.domain.repository.AssessmentRepository
import com.eduvark.app.domain.repository.AuthRepository
import com.eduvark.app.domain.repository.CaseRepository
import com.eduvark.app.domain.repository.FeedbackRepository
import com.eduvark.app.domain.repository.HealthRepository
import com.eduvark.app.domain.repository.MaterialContentSource
import com.eduvark.app.domain.repository.MaterialFileStore
import com.eduvark.app.domain.repository.TopicRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindHealthRepository(impl: HealthRepositoryImpl): HealthRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindTopicRepository(impl: TopicRepositoryImpl): TopicRepository

    @Binds
    @Singleton
    abstract fun bindMaterialFileStore(impl: AndroidMaterialFileStore): MaterialFileStore

    @Binds
    @Singleton
    abstract fun bindMaterialContentSource(impl: HttpMaterialContentSource): MaterialContentSource

    @Binds
    @Singleton
    abstract fun bindFeedbackRepository(impl: FeedbackRepositoryImpl): FeedbackRepository

    @Binds
    @Singleton
    abstract fun bindCaseRepository(impl: CaseRepositoryImpl): CaseRepository

    @Binds
    @Singleton
    abstract fun bindAssessmentRepository(impl: AssessmentRepositoryImpl): AssessmentRepository

    @Binds
    @Singleton
    abstract fun bindAnalyticsRepository(impl: AnalyticsRepositoryImpl): AnalyticsRepository

    @Binds
    @Singleton
    abstract fun bindAdminRepository(impl: AdminRepositoryImpl): AdminRepository
}
