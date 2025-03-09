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
package com.bendb.thrifty.gradle;

import java.io.File;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkParameters;

/** Encapsulates all input to Thrifty compilation, in a {@link java.io.Serializable Serializable} form. */
public interface GenerateThriftSourcesWorkParams extends WorkParameters {
    DirectoryProperty getOutputDirectory();

    ListProperty<File> getIncludePath();

    ConfigurableFileCollection getSource();

    Property<ShowStacktrace> getShowStacktrace();

    Property<Boolean> getIsGenerateServiceClients();

    Property<FieldNameStyle> getNameStyle();

    Property<String> getListType();

    Property<String> getSetType();

    Property<String> getMapType();

    Property<Boolean> getIsParcelable();

    Property<Boolean> getIsAllowUnknownEnumValues();

    Property<Boolean> getIsGenerateServer();
}
