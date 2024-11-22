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
package com.bendb.thrifty.integration

import com.bendb.thrifty.protocol.FieldMetadata
import com.bendb.thrifty.protocol.ListMetadata
import com.bendb.thrifty.protocol.MapMetadata
import com.bendb.thrifty.protocol.MessageMetadata
import com.bendb.thrifty.protocol.Protocol
import com.bendb.thrifty.protocol.SetMetadata
import com.bendb.thrifty.protocol.StructMetadata
import okio.ByteString

/**
 * A [Protocol] wrapper that prints each method as it is invoked, which is
 * helpful when debugging client-server errors from generated code.
 */
class DebugProtocolWrapper(
        private val protocol: Protocol
) : Protocol by protocol {
    override suspend fun writeMessageBegin(name: String, typeId: Byte, seqId: Int) {
        println("writeMessageBegin($name, $typeId, $seqId")
        protocol.writeMessageBegin(name, typeId, seqId)
    }

    override suspend fun writeMessageEnd() {
        println("writeMessageEnd")
        protocol.writeMessageEnd()
    }

    override suspend fun writeStructBegin(structName: String) {
        println("writeStructBegin($structName)")
        protocol.writeStructBegin(structName)
    }

    override suspend fun writeStructEnd() {
        println("writeStructEnd()")
        protocol.writeStructEnd()
    }

    override suspend fun writeFieldBegin(fieldName: String, fieldId: Int, typeId: Byte) {
        println("writeFieldBegin($fieldName, $fieldId, $typeId)")
        protocol.writeFieldBegin(fieldName, fieldId, typeId)
    }

    override suspend fun writeFieldEnd() {
        println("writeFieldEnd()")
        protocol.writeFieldEnd()
    }

    override suspend fun writeFieldStop() {
        println("writeFieldStop()")
        protocol.writeFieldStop()
    }

    override suspend fun writeMapBegin(keyTypeId: Byte, valueTypeId: Byte, mapSize: Int) {
        println("writeMapBegin($keyTypeId, $valueTypeId, $mapSize)")
        protocol.writeMapBegin(keyTypeId, valueTypeId, mapSize)
    }

    override suspend fun writeMapEnd() {
        println("writeMapEnd()")
        protocol.writeMapEnd()
    }

    override suspend fun writeListBegin(elementTypeId: Byte, listSize: Int) {
        println("writeListBegin($elementTypeId, $listSize)")
        protocol.writeListBegin(elementTypeId, listSize)
    }

    override suspend fun writeListEnd() {
        println("writeListEnd()")
        protocol.writeListEnd()
    }

    override suspend fun writeSetBegin(elementTypeId: Byte, setSize: Int) {
        println("writeSetBegin($elementTypeId, $setSize)")
        protocol.writeSetBegin(elementTypeId, setSize)
    }

    override suspend fun writeSetEnd() {
        println("writeSetEnd()")
        protocol.writeSetEnd()
    }

    override suspend fun writeBool(b: Boolean) {
        println("writeBool($b)")
        protocol.writeBool(b)
    }

    override suspend fun writeByte(b: Byte) {
        println("writeByte($b)")
        protocol.writeByte(b)
    }

    override suspend fun writeI16(i16: Short) {
        println("writeI16($i16)")
        protocol.writeI16(i16)
    }

    override suspend fun writeI32(i32: Int) {
        println("writeI32($i32)")
        protocol.writeI32(i32)
    }

    override suspend fun writeI64(i64: Long) {
        println("writeI64($i64)")
        protocol.writeI64(i64)
    }

    override suspend fun writeDouble(dub: Double) {
        println("writeDouble($dub)")
        protocol.writeDouble(dub)
    }

    override suspend fun writeString(str: String) {
        println("writeString($str)")
        protocol.writeString(str)
    }

    override suspend fun writeBinary(buf: ByteString) {
        println("writeBinary(${buf.hex()})")
        protocol.writeBinary(buf)
    }

    override suspend fun readMessageBegin(): MessageMetadata {
        println("readMessageBegin()")
        return protocol.readMessageBegin()
    }

    override suspend fun readMessageEnd() {
        println("readMessageEnd()")
        protocol.readMessageEnd()
    }

    override suspend fun readStructBegin(): StructMetadata {
        println("readStructBegin()")
        return protocol.readStructBegin()
    }

    override suspend fun readStructEnd() {
        println("readStructEnd()")
        protocol.readStructEnd()
    }

    override suspend fun readFieldBegin(): FieldMetadata {
        println("readFieldBegin()")
        return protocol.readFieldBegin()
    }

    override suspend fun readFieldEnd() {
        println("readFieldEnd()")
        protocol.readFieldEnd()
    }

    override suspend fun readMapBegin(): MapMetadata {
        println("readMapBegin()")
        return protocol.readMapBegin()
    }

    override suspend fun readMapEnd() {
        println("readMapEnd()")
        protocol.readMapEnd()
    }

    override suspend fun readListBegin(): ListMetadata {
        println("readListBegin()")
        return protocol.readListBegin()
    }

    override suspend fun readListEnd() {
        println("readListEnd()")
        protocol.readListEnd()
    }

    override suspend fun readSetBegin(): SetMetadata {
        println("readSetBegin()")
        return protocol.readSetBegin()
    }

    override suspend fun readSetEnd() {
        println("readSetEnd()")
        protocol.readSetEnd()
    }

    override suspend fun readBool(): Boolean {
        println("readBool()")
        return protocol.readBool()
    }

    override suspend fun readByte(): Byte {
        println("readByte()")
        return protocol.readByte()
    }

    override suspend fun readI16(): Short {
        println("readI16()")
        return protocol.readI16()
    }

    override suspend fun readI32(): Int {
        println("readI32()")
        return protocol.readI32()
    }

    override suspend fun readI64(): Long {
        println("readI64()")
        return protocol.readI64()
    }

    override suspend fun readDouble(): Double {
        println("readDouble()")
        return protocol.readDouble()
    }

    override suspend fun readString(): String {
        println("readString()")
        return protocol.readString()
    }

    override suspend fun readBinary(): ByteString {
        println("readBinary()")
        return protocol.readBinary()
    }

    override suspend fun reset() {
        println("reset()")
        protocol.reset()
    }
}
