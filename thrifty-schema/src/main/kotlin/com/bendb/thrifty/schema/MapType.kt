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
package com.bendb.thrifty.schema

/**
 * Represents a Thrift `map<K, V>`.
 *
 * @property keyType The type of keys contained in maps of this type.
 * @property valueType The type of values contained in maps of this type.
 */
class MapType
internal constructor(
    val keyType: ThriftType,
    val valueType: ThriftType,
    override val annotations: Map<String, String> = emptyMap()
) : ThriftType("map<" + keyType.name + ", " + valueType.name + ">") {

  override val isMap: Boolean = true

  override fun <T> accept(visitor: ThriftType.Visitor<T>): T = visitor.visitMap(this)

  override fun withAnnotations(annotations: Map<String, String>): ThriftType {
    return MapType(keyType, valueType, mergeAnnotations(this.annotations, annotations))
  }

  /** Creates a [Builder] initialized with this map type. */
  fun toBuilder(): Builder = Builder(this)

  /** An object that can create [MapType] instances. */
  class Builder(
      private var keyType: ThriftType,
      private var valueType: ThriftType,
      private var annotations: Map<String, String>
  ) {
    internal constructor(type: MapType) : this(type.keyType, type.valueType, type.annotations)

    /** Use the given [keyType] for the [MapType] under construction. */
    fun keyType(keyType: ThriftType): Builder = apply { this.keyType = keyType }

    /** Use the given [valueType] for the [MapType] under construction. */
    fun valueType(valueType: ThriftType): Builder = apply { this.valueType = valueType }

    /** Use the given [annotations] for the [MapType] under construction. */
    fun annotations(annotations: Map<String, String>): Builder = apply {
      this.annotations = annotations
    }

    /** Creates a new [MapType] instance. */
    fun build(): MapType {
      return MapType(keyType, valueType, annotations)
    }
  }
}
