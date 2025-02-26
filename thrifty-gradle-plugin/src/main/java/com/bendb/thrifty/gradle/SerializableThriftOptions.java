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

import java.io.Serializable;

// Can't just use ThriftOptions cuz Gradle decorates them with non-serializable types,
// and we need to pass these options to Worker API params that must be serializable.
class SerializableThriftOptions implements Serializable {
    private boolean generateServiceClients = true;
    private FieldNameStyle nameStyle = FieldNameStyle.DEFAULT;
    private String listType = null;
    private String setType = null;
    private String mapType = null;
    private boolean parcelable = false;
    private boolean allowUnknownEnumValues = false;
    private boolean generateServer = false;

    // For Serializable
    SerializableThriftOptions() {}

    SerializableThriftOptions(ThriftOptions options) {
        this.generateServiceClients = options.getGenerateServiceClients();
        this.nameStyle = options.getNameStyle();
        this.listType = options.getListType();
        this.setType = options.getSetType();
        this.mapType = options.getMapType();
        this.parcelable = options.getParcelable();
        this.allowUnknownEnumValues = options.getAllowUnknownEnumValues();
        this.generateServer = options.isGenerateServer();
    }

    public boolean isGenerateServiceClients() {
        return generateServiceClients;
    }

    public FieldNameStyle getNameStyle() {
        return nameStyle;
    }

    public String getListType() {
        return listType;
    }

    public String getSetType() {
        return setType;
    }

    public String getMapType() {
        return mapType;
    }

    public boolean isParcelable() {
        return parcelable;
    }

    public boolean isAllowUnknownEnumValues() {
        return allowUnknownEnumValues;
    }

    public boolean isGenerateServer() {
        return generateServer;
    }
}
