/*
 * Thrifty
 *
 * Copyright (c) Benjamin Bader
 *
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * THIS CODE IS PROVIDED ON AN  *AS IS* BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING
 * WITHOUT LIMITATION ANY IMPLIED WARRANTIES OR CONDITIONS OF TITLE,
 * FITNESS FOR A PARTICULAR PURPOSE, MERCHANTABLITY OR NON-INFRINGEMENT.
 *
 * See the Apache Version 2.0 License for specific language governing permissions and limitations under the License.
 */
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
