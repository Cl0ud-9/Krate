package dev.cl0ud9.krate.domain.model

// app support lifecycle state, section 42.19 of the spec
enum class SupportStatus {
    SUPPORTED,
    BETA,
    DEPRECATED,
    TEMPORARILY_UNAVAILABLE,
}
