package dev.cl0ud9.krate.ui.apps

import dev.cl0ud9.krate.domain.model.AppProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val URL = Regex("""https?://\S+""")
private val GITHUB_REPO = Regex("""^https?://(?:www\.)?github\.com/([\w.-]+)/([\w.-]+)""", RegexOption.IGNORE_CASE)
private const val SHORT_TEXT_CHARS = 40

// what someone shared to Krate, turned into a ready-to-send suggestion
data class SharedSuggestion(
    val name: String,
    val link: String,
    val why: String = "",
    // owner/repo when the link is a GitHub repository, for spotting an app that's already in the Krate
    val githubRepo: String? = null,
)

// the first link in the shared text names the app; a GitHub link is cut to its repository, and the repository's name
// (dashes and underscores made into spaces) stands in for the app's. Shared text with no link at all is kept as the
// name when it's short, or the reason when it isn't
fun parseSharedSuggestion(text: String?): SharedSuggestion? {
    val shared = text?.trim().orEmpty()
    val url = URL.find(shared)?.value?.trimEnd('.', ',', ')', '>')
    val repo = url?.let { GITHUB_REPO.find(it) }
    return when {
        shared.isEmpty() -> null
        url == null && shared.length <= SHORT_TEXT_CHARS -> SharedSuggestion(name = shared, link = "")
        url == null -> SharedSuggestion(name = "", link = "", why = shared)
        repo == null -> SharedSuggestion(name = "", link = url)
        else -> {
            val (owner, name) = repo.destructured
            val repoName = name.removeSuffix(".git")
            SharedSuggestion(
                name = repoName.replace('-', ' ').replace('_', ' '),
                link = "https://github.com/$owner/$repoName",
                githubRepo = "$owner/$repoName",
            )
        }
    }
}

// the catalog app published from this GitHub repository, if Krate already carries it
fun List<AppProfile>.publishedFrom(githubRepo: String): AppProfile? =
    firstOrNull { app ->
        app.artifacts.any { it.downloadUrl.contains("github.com/$githubRepo/", ignoreCase = true) }
    }

// a suggestion shared in from another app, waiting for the main screen to open the sheet with it
object SharedSuggestions {
    private val mutablePending = MutableStateFlow<SharedSuggestion?>(null)
    val pending: StateFlow<SharedSuggestion?> = mutablePending.asStateFlow()

    fun offer(suggestion: SharedSuggestion) {
        mutablePending.value = suggestion
    }

    fun clear() {
        mutablePending.value = null
    }
}
