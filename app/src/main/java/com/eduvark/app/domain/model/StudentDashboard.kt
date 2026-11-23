package com.eduvark.app.domain.model

/**
 * Counts are topic-based and scoped to the student's active class and VARK.
 * A topic counts as not completed when the student has no feedback (V/A/R)
 * or no case submission (Kinesthetic) for it.
 */
data class StudentDashboard(
    val vark: VarkType,
    val availableMaterials: Int,
    val notCompletedMaterials: Int,
)
