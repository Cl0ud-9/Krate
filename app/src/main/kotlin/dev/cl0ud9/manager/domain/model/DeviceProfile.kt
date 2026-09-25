package dev.cl0ud9.manager.domain.model

// what the catalog needs to know about this device to hide builds it cannot run - a Material You
// build needs Android 12+, an arm64-only build needs an arm64 CPU
data class DeviceProfile(
    val sdkInt: Int,
    val supportedAbis: List<String>,
    val managerVersionCode: Long,
)
