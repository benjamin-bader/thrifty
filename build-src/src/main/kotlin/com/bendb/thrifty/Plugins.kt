/*
 * Thrifty
 *
 * Copyright (c) Benjamin Bader
 * Copyright (c) Microsoft Corporation
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

object Plugins {
    const val JAVA = "java-library"
    const val TEST_SUITE = "jvm-test-suite"
    const val IDEA = "idea"
    const val JACOCO = "jacoco"

    const val KOTLIN_JVM = "org.jetbrains.kotlin.jvm"
    const val KOTLIN_MPP = "org.jetbrains.kotlin.multiplatform"
    const val DOKKA = "org.jetbrains.dokka"

    const val MAVEN_PUBLISH = "com.vanniktech.maven.publish"

    const val SPOTLESS = "com.diffplug.spotless"
}
