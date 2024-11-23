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
package com.bendb.thrifty.protocol

import okio.ByteString
import okio.Closeable
import okio.IOException

interface Protocol : Closeable {

    suspend fun writeMessageBegin(name: String, typeId: Byte, seqId: Int)

    suspend fun writeMessageEnd()

    suspend fun writeStructBegin(structName: String)

    suspend fun writeStructEnd()

    suspend fun writeFieldBegin(fieldName: String, fieldId: Int, typeId: Byte)

    suspend fun writeFieldEnd()

    suspend fun writeFieldStop()

    suspend fun writeMapBegin(keyTypeId: Byte, valueTypeId: Byte, mapSize: Int)

    suspend fun writeMapEnd()

    suspend fun writeListBegin(elementTypeId: Byte, listSize: Int)

    suspend fun writeListEnd()

    suspend fun writeSetBegin(elementTypeId: Byte, setSize: Int)

    suspend fun writeSetEnd()

    suspend fun writeBool(b: Boolean)

    suspend fun writeByte(b: Byte)

    suspend fun writeI16(i16: Short)

    suspend fun writeI32(i32: Int)

    suspend fun writeI64(i64: Long)

    suspend fun writeDouble(dub: Double)

    suspend fun writeString(str: String)

    suspend fun writeBinary(buf: ByteString)

    ////////

    suspend fun readMessageBegin(): MessageMetadata

    suspend fun readMessageEnd()

    suspend fun readStructBegin(): StructMetadata

    suspend fun readStructEnd()

    suspend fun readFieldBegin(): FieldMetadata

    suspend fun readFieldEnd()

    suspend fun readMapBegin(): MapMetadata

    suspend fun readMapEnd()

    suspend fun readListBegin(): ListMetadata

    suspend fun readListEnd()

    suspend fun readSetBegin(): SetMetadata

    suspend fun readSetEnd()

    suspend fun readBool(): Boolean

    suspend fun readByte(): Byte

    suspend fun readI16(): Short

    suspend fun readI32(): Int

    suspend fun readI64(): Long

    suspend fun readDouble(): Double

    suspend fun readString(): String

    suspend fun readBinary(): ByteString

    //////////////

    suspend fun flush()

    suspend fun reset() {
        // to be implemented by implementations as needed
    }
}
