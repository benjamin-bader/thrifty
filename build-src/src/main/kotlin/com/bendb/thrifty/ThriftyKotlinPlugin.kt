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

import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Applies Kotlin language plugins and configures them.
 *
 * All this, just to make Dokka optional.
 */
class ThriftyKotlinPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        applyBasePlugins(project)
        configureToolchain(project)
        addKotlinBom(project)
        configureKotlinTasks(project)
        configureSpotless(project)
    }

    private fun applyBasePlugins(project: Project) {
        project.plugins.apply<ThriftyJavaPlugin>()
        project.plugins.apply(Plugins.KOTLIN_JVM)
        if (project.shouldSignAndDocumentBuild) {
            project.plugins.apply(Plugins.DOKKA)
        }
    }

    private fun configureToolchain(project: Project) {
        val ext = project.extensions.findByType<KotlinProjectExtension>()!!
        ext.jvmToolchain(Toolchain::apply)
    }

    private fun addKotlinBom(project: Project) {
        val catalogs = project.extensions.findByType<VersionCatalogsExtension>()!!
        val catalog = catalogs.named("libs")
        val maybeBomProvider = catalog.findLibrary("kotlin-bom")
        check(maybeBomProvider.isPresent) { "No kotlin-bom dependency found" }

        val kotlinBom: Dependency = maybeBomProvider.get().get()
        val kotlinBomPlatformDependency = project.dependencies.platform(kotlinBom)
        project.configurations
            .getByName("api")
            .dependencies
            .add(kotlinBomPlatformDependency)
    }

    private fun configureKotlinTasks(project: Project) {
        val kotlinCompileTasks = project.tasks.withType<KotlinCompile>()
        kotlinCompileTasks.configureEach { task ->
            task.compilerOptions {
                jvmTarget.set(JvmTarget.JVM_1_8)
                freeCompilerArgs.add("-Xjvm-default=all")
            }
        }
    }

    private fun configureSpotless(project: Project) {
        // spotless has already been applied by the Java plugin

        val catalogs = project.extensions.findByType<VersionCatalogsExtension>()!!
        val catalog = catalogs.named("libs")
        val maybeKtfmtVersion = catalog.findVersion("ktfmt")
        check(maybeKtfmtVersion.isPresent) { "No kotlin-bom dependency found" }

        val generatedCode = project.layout.buildDirectory.asFileTree.matching {
            it.include("generated/**/*.kt")
            it.include("generated-src/**/*.kt") // integration tests
        }

        project.extensions.configure(SpotlessExtension::class.java) { ext ->
            ext.kotlin { kt ->
                kt.ktfmt(maybeKtfmtVersion.get().toString())

                kt.toggleOffOn()

                kt.targetExclude(generatedCode)
            }
        }
    }
}
