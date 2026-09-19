package dev.cl0ud9.manager.data.downloads

// surfaces a download's state while the app itself isn't on screen to show it - callers report
// every state transition unconditionally, the implementation decides whether that's actually worth
// showing (e.g. progress only while the app is backgrounded, since the in-app UI already covers
// the foreground case - but a terminal complete/failed result stays posted even if the user
// returns to the app before dismissing it, matching how a normal download-manager notification behaves)
interface DownloadProgressNotifier {
    fun onDownloading(
        appId: String,
        appName: String,
        bytesDownloaded: Long,
        totalBytes: Long?,
    )

    fun onVerifying(
        appId: String,
        appName: String,
    )

    fun onComplete(
        appId: String,
        appName: String,
    )

    fun onFailed(
        appId: String,
        appName: String,
        reason: String,
    )

    fun clear(appId: String)
}
