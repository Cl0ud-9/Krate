# R8 is on for release builds (isMinifyEnabled). The libraries in use ship their own consumer rules
# (kotlinx.serialization, OkHttp, Tink, WorkManager, DataStore); add a rule here only for something
# that is found breaking at runtime.
