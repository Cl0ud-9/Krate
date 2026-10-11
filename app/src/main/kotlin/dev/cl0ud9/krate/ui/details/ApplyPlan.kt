package dev.cl0ud9.krate.ui.details

import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.AutoBackup
import dev.cl0ud9.krate.domain.model.RecommendedSettings
import dev.cl0ud9.krate.platform.backup.RecommendedChoice

// what the apply buttons need to know
internal class ApplyPlan(
    val app: AppProfile,
    val spec: AutoBackup,
    val recommended: RecommendedSettings,
    val choice: RecommendedChoice,
    val canUndo: Boolean,
)
