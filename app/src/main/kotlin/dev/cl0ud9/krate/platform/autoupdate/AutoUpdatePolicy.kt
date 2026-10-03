package dev.cl0ud9.krate.platform.autoupdate

import dev.cl0ud9.krate.domain.dependency.DependencyGraph
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.InstallationMode
import dev.cl0ud9.krate.domain.model.latestArtifact
import dev.cl0ud9.krate.domain.repository.AutoUpdateChoices
import dev.cl0ud9.krate.domain.repository.buildKey
import dev.cl0ud9.krate.platform.packageinfo.InstalledVersion
import dev.cl0ud9.krate.ui.details.requiresUninstall

// a release waits this long before going on by itself, so a broken build is usually caught (and withdrawn) first
internal const val AUTO_UPDATE_DELAY_MILLIS = 24L * 60 * 60 * 1000

// a build that fails this often is left for the user to install
internal const val MAX_AUTO_UPDATE_TRIES = 2

// an app as it is on the phone
internal data class AutoUpdateFacts(
    val installed: InstalledVersion?,
    // Krate installed it, and nothing else has claimed its updates since
    val krateOwnsUpdates: Boolean,
)

// which pending updates go on by themselves; pending apps are already on their theme's track
internal class AutoUpdatePolicy(
    private val catalog: List<AppProfile>,
    private val choices: AutoUpdateChoices,
    private val tries: (appId: String, buildKey: String) -> Int,
    private val facts: (AppProfile) -> AutoUpdateFacts,
) {
    // true once it's this app's turn, whatever the wait still left; a rollback, a reinstall from scratch, an app
    // another store owns, a missing dependency or the user's own choice keep it on manual
    fun willUpdate(app: AppProfile): Boolean {
        val artifact = app.latestArtifact ?: return false
        val phone = facts(app)
        return app.id !in choices.manual &&
            !choices.isSkipped(app.id, artifact) &&
            tries(app.id, artifact.buildKey) < MAX_AUTO_UPDATE_TRIES &&
            app.installationMode != InstallationMode.CLEAN_INSTALL &&
            phone.installed != null &&
            phone.krateOwnsUpdates &&
            !requiresUninstall(phone.installed, artifact) &&
            artifact.publishedAtMillis != null &&
            DependencyGraph.directDependencies(app, catalog).all { facts(it).installed != null }
    }

    fun isDue(
        app: AppProfile,
        nowMillis: Long,
    ): Boolean {
        val published = app.latestArtifact?.publishedAtMillis ?: return false
        return willUpdate(app) && nowMillis - published >= AUTO_UPDATE_DELAY_MILLIS
    }

    // when it goes on by itself, or null when it won't
    fun dueAt(app: AppProfile): Long? =
        app.latestArtifact
            ?.publishedAtMillis
            ?.takeIf { willUpdate(app) }
            ?.plus(AUTO_UPDATE_DELAY_MILLIS)

    // dependencies before the apps that need them
    fun inInstallOrder(apps: List<AppProfile>): List<AppProfile> {
        val due = apps.associateBy { it.id }
        return apps
            .flatMap { app -> DependencyGraph.installOrder(app, catalog) ?: listOf(app) }
            .mapNotNull { due[it.id] }
            .distinctBy { it.id }
    }
}

// still not on a day after Krate first found it due (never downloaded, or the app always in use): the user hears of it
internal fun isOverdue(
    dueSinceMillis: Long,
    nowMillis: Long,
): Boolean = nowMillis - dueSinceMillis > AUTO_UPDATE_DELAY_MILLIS
