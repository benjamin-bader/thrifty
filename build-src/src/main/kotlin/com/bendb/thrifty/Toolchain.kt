package com.bendb.thrifty

import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainSpec

/**
 * Specifies the toolchain that we use for compilation.  Any
 * constraints should be added here, not in the java or kotlin
 * plugins.
 *
 * At present, we just want JDK 19, but might constrain by vendor
 * in the future.
 */
object Toolchain {
    val languageVersion = JavaLanguageVersion.of(21)

    fun apply(spec: JavaToolchainSpec) {
        spec.languageVersion.set(languageVersion)
    }
}
