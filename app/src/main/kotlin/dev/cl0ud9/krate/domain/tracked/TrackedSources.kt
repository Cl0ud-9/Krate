package dev.cl0ud9.krate.domain.tracked

// where an app's code and releases live; the id is what's saved, so it never changes
enum class Forge(
    val id: String,
    val host: String,
    val siteName: String,
) {
    GITHUB("github", "github.com", "GitHub"),
    CODEBERG("codeberg", "codeberg.org", "Codeberg"),
    GITLAB("gitlab", "gitlab.com", "GitLab"),
    ;

    companion object {
        // anything saved before other sites were followed is a GitHub one
        fun fromId(id: String?): Forge = entries.firstOrNull { it.id == id } ?: GITHUB
    }
}

// a repository someone wants Krate to follow; on GitLab the owner can be a group with subgroups, a/b/c
data class RepoRef(
    val owner: String,
    val repo: String,
    val forge: Forge = Forge.GITHUB,
) {
    val slug: String get() = "$owner/$repo"

    val webUrl: String get() = "https://${forge.host}/$slug"
}

// one file attached to a release
data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    // the site's own digest of the file, when it publishes one
    val sha256: String? = null,
)

private const val SEGMENT = "[A-Za-z0-9._-]+"
private val SITE_LINK =
    Regex("""^(?:https?://)?(?:www\.)?(github\.com|codeberg\.org|gitlab\.com)/(.+)$""", RegexOption.IGNORE_CASE)
private val BARE_SLUG = Regex("""^($SEGMENT)/($SEGMENT)$""")
private val NAME = Regex(SEGMENT)

// the sites' own pages that look like an owner in a link but aren't one
private val NOT_OWNERS =
    setOf(
        "orgs",
        "settings",
        "topics",
        "features",
        "marketplace",
        "sponsors",
        "login",
        "explore",
        "users",
        "user",
        "dashboard",
        "help",
        "admin",
        "api",
        "-",
    )

// a repository from whatever people copy: the full link, a link to its releases or one file in it, the link without
// the https, or just owner/repo (taken to be on GitHub). GitHub, Codeberg and GitLab; null for anything else
fun parseRepoLink(text: String): RepoRef? {
    val input = text.trim().trimEnd('/')
    val site = SITE_LINK.matchEntire(input)
    val bare = BARE_SLUG.matchEntire(input)
    return when {
        site != null -> siteRepo(site.groupValues[1], site.groupValues[2])
        bare != null -> repoRef(listOf(bare.groupValues[1], bare.groupValues[2]), Forge.GITHUB)
        else -> null
    }
}

private fun siteRepo(
    host: String,
    rest: String,
): RepoRef? {
    val forge = Forge.entries.first { it.host.equals(host, ignoreCase = true) }
    val path = rest.substringBefore('?').substringBefore('#')
    val segments =
        when (forge) {
            // GitLab marks where a project's own pages start with "/-/"; everything before it is the project
            Forge.GITLAB -> path.substringBefore("/-/").split('/')
            else -> path.split('/').take(2)
        }.filter { it.isNotEmpty() }
    return repoRef(segments, forge)
}

private fun repoRef(
    segments: List<String>,
    forge: Forge,
): RepoRef? {
    val names = segments.mapIndexed { i, name -> if (i == segments.lastIndex) name.removeSuffix(".git") else name }
    val valid =
        names.size >= 2 &&
            names.all { NAME.matches(it) && it != "." && it != ".." } &&
            names.first().lowercase() !in NOT_OWNERS &&
            (forge == Forge.GITLAB || names.size == 2)
    return if (valid) RepoRef(names.dropLast(1).joinToString("/"), names.last(), forge) else null
}

// what a release's tag says the version is: v1.2.0 is 1.2.0
fun versionFromTag(tag: String): String =
    tag
        .trim()
        .removePrefix("v")
        .removePrefix("V")
        .ifBlank { tag }

// processor families as release files name them; longer names first, so x86_64 is never read as x86
private val ABI_NAMES =
    listOf(
        "arm64-v8a" to "arm64-v8a",
        "arm64" to "arm64-v8a",
        "aarch64" to "arm64-v8a",
        "armeabi-v7a" to "armeabi-v7a",
        "armv7" to "armeabi-v7a",
        "armeabi" to "armeabi-v7a",
        "x86_64" to "x86_64",
        "x64" to "x86_64",
        "x86" to "x86",
    )
private const val UNIVERSAL = "universal"
private const val BEST = 0
private const val ANY_PHONE = 1
private const val ALSO_RUNS = 2

// the processor family a file is built for, from its name; null when it doesn't say (usually one build for every phone)
fun abiOf(fileName: String): String? {
    val name = fileName.lowercase()
    return ABI_NAMES
        .firstOrNull { (token, _) ->
            Regex("""(^|[^a-z0-9])${Regex.escape(token)}([^a-z0-9]|$)""").containsMatchIn(name)
        }?.second
}

// the app files in a release that can run on this phone, the best fit first: built for its own processor, then one
// build for every phone, then any other family it can also run. Debug builds only count when there's nothing else
fun apkCandidates(
    assets: List<ReleaseAsset>,
    supportedAbis: List<String>,
): List<ReleaseAsset> {
    val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
    val runnable =
        apks.filter { asset ->
            val abi = abiOf(asset.name)
            abi == null || abi in supportedAbis
        }
    val release = runnable.filterNot { "debug" in it.name.lowercase() }.ifEmpty { runnable }
    return release.sortedBy { asset ->
        val abi = abiOf(asset.name)
        when {
            abi != null && abi == supportedAbis.firstOrNull() -> BEST
            abi == null || UNIVERSAL in asset.name.lowercase() -> ANY_PHONE
            else -> ALSO_RUNS + supportedAbis.indexOf(abi)
        }
    }
}

private val VERSION_NUMBER = Regex("""\d+(?:[._-]\d+)*""")
private const val ANY_VERSION = """\d+(?:[._-]\d+)*"""

// the chosen file's name with its version numbers left open, so the same kind of file is found in the next release:
// app-1.2.0-arm64-v8a.apk becomes app-<numbers>-arm64-v8a.apk. Processor names keep their own numbers
fun nameShape(fileName: String): String {
    val protected =
        ABI_NAMES.map { it.first }.flatMap { token ->
            Regex(Regex.escape(token), RegexOption.IGNORE_CASE).findAll(fileName).map { it.range }
        }
    val pattern = StringBuilder("^")
    var last = 0
    VERSION_NUMBER.findAll(fileName).filter { found -> protected.none { found.range.first in it } }.forEach { found ->
        pattern.append(Regex.escape(fileName.substring(last, found.range.first)))
        pattern.append(ANY_VERSION)
        last = found.range.last + 1
    }
    pattern.append(Regex.escape(fileName.substring(last))).append("$")
    return pattern.toString()
}

// the file in a release that matches what was picked before; the best fit for this phone when the project renamed
// its files, so a rename doesn't leave the app stuck on its last version
fun pickAsset(
    assets: List<ReleaseAsset>,
    shape: String,
    supportedAbis: List<String>,
): ReleaseAsset? {
    val candidates = apkCandidates(assets, supportedAbis)
    val regex = runCatching { Regex(shape, RegexOption.IGNORE_CASE) }.getOrNull()
    return candidates.firstOrNull { regex?.matches(it.name) == true } ?: candidates.firstOrNull()
}
