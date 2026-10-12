plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

// The wrapper task normally HEAD-tests the distribution URL, which fails on
// this machine (gradle.org's CDN is not reachable from the sandbox). We
// already have the exact same distribution unpacked under .tools/, so skip
// the check and let the wrapper be generated with a plain distributionUrl.
tasks.wrapper {
    distributionType = org.gradle.api.tasks.wrapper.Wrapper.DistributionType.BIN
    validateDistributionUrl = false
}
