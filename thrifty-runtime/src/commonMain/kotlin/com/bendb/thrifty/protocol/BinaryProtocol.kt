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

import com.bendb.thrifty.TType
import com.bendb.thrifty.transport.Transport
import okio.ByteString
import okio.ByteString.Companion.toByteString
import okio.EOFException
import kotlin.jvm.JvmOverloads

/**
 * An implementation of the simple Thrift binary protocol.
 *
 * Instances of this class are *not* threadsafe.
 *
 * @param transport
 * @param stringLengthLimit
 *      The maximum number of bytes to read from the transport for
 *      variable-length fields (strings or binary), or -1 for unlimited.
 * @param containerLengthLimit
 *      The maximum number of elements to read from the network for containers
 *      (maps, lists, sets).
 */
class BinaryProtocol @JvmOverloads constructor(
        transport: Transport,
        private val stringLengthLimit: Long = -1,
        private val containerLengthLimit: Long = -1
) : BaseProtocol(transport) {
    /**
     * A shared buffer for writing.
     */
    private val buffer = ByteArray(8)
    private val strictRead = false
    private val strictWrite = false

    override suspend fun writeMessageBegin(name: String, typeId: Byte, seqId: Int) {
        if (strictWrite) {
            val version = VERSION_1 or (typeId.toInt() and 0xFF)
            writeI32(version)
            writeString(name)
            writeI32(seqId)
        } else {
            writeString(name)
            writeByte(typeId)
            writeI32(seqId)
        }
    }

    override suspend fun writeMessageEnd() {
    }

    override suspend fun writeStructBegin(structName: String) {
    }

    override suspend fun writeStructEnd() {
    }

    override suspend fun writeFieldBegin(fieldName: String, fieldId: Int, typeId: Byte) {
        writeByte(typeId)
        writeI16(fieldId.toShort())
    }

    override suspend fun writeFieldEnd() {
    }

    override suspend fun writeFieldStop() {
        writeByte(TType.STOP)
    }

    override suspend fun writeMapBegin(keyTypeId: Byte, valueTypeId: Byte, mapSize: Int) {
        writeByte(keyTypeId)
        writeByte(valueTypeId)
        writeI32(mapSize)
    }

    override suspend fun writeMapEnd() {
    }

    override suspend fun writeListBegin(elementTypeId: Byte, listSize: Int) {
        writeByte(elementTypeId)
        writeI32(listSize)
    }

    override suspend fun writeListEnd() {
    }

    override suspend fun writeSetBegin(elementTypeId: Byte, setSize: Int) {
        writeByte(elementTypeId)
        writeI32(setSize)
    }

    override suspend fun writeSetEnd() {
    }

    override suspend fun writeBool(b: Boolean) {
        writeByte(if (b) 1.toByte() else 0.toByte())
    }

    override suspend fun writeByte(b: Byte) {
        buffer[0] = b
        transport.write(buffer, 0, 1)
    }

    override suspend fun writeI16(i16: Short) {
        buffer[0] = ((i16.toInt() shr 8) and 0xFF).toByte()
        buffer[1] = (i16.toInt() and 0xFF).toByte()
        transport.write(buffer, 0, 2)
    }

    override suspend fun writeI32(i32: Int) {
        buffer[0] = ((i32 shr 24) and 0xFF).toByte()
        buffer[1] = ((i32 shr 16) and 0xFF).toByte()
        buffer[2] = ((i32 shr 8) and 0xFF).toByte()
        buffer[3] = (i32 and 0xFF).toByte()
        transport.write(buffer, 0, 4)
    }

    override suspend fun writeI64(i64: Long) {
        buffer[0] = ((i64 shr 56) and 0xFF).toByte()
        buffer[1] = ((i64 shr 48) and 0xFF).toByte()
        buffer[2] = ((i64 shr 40) and 0xFF).toByte()
        buffer[3] = ((i64 shr 32) and 0xFF).toByte()
        buffer[4] = ((i64 shr 24) and 0xFF).toByte()
        buffer[5] = ((i64 shr 16) and 0xFF).toByte()
        buffer[6] = ((i64 shr 8) and 0xFF).toByte()
        buffer[7] = (i64 and 0xFF).toByte()
        transport.write(buffer, 0, 8)
    }

    override suspend fun writeDouble(dub: Double) {
        writeI64(dub.toRawBits())
    }

    override suspend fun writeString(str: String) {
        val bs = str.encodeToByteArray()
        writeI32(bs.size)
        transport.write(bs)
    }

    override suspend fun writeBinary(buf: ByteString) {
        writeI32(buf.size)
        transport.write(buf.toByteArray())
    }

    //////////////////////
    override suspend fun readMessageBegin(): MessageMetadata {
        val size = readI32()
        return if (size < 0) {
            val version = size and VERSION_MASK
            if (version != VERSION_1) {
                throw ProtocolException("Bad version in readMessageBegin")
            }
            MessageMetadata(readString(), (size and 0xff).toByte(), readI32())
        } else {
            if (strictRead) {
                throw ProtocolException("Missing version in readMessageBegin")
            }
            MessageMetadata(readStringWithSize(size), readByte(), readI32())
        }
    }

    override suspend fun readMessageEnd() {
    }

    override suspend fun readStructBegin(): StructMetadata {
        return NO_STRUCT
    }

    override suspend fun readStructEnd() {
    }

    override suspend fun readFieldBegin(): FieldMetadata {
        val typeId = readByte()
        val fieldId = if (typeId == TType.STOP) 0 else readI16()
        return FieldMetadata("", typeId, fieldId)
    }

    override suspend fun readFieldEnd() {
    }

    override suspend fun readMapBegin(): MapMetadata {
        val keyTypeId = readByte()
        val valueTypeId = readByte()
        val size = readI32()
        if (containerLengthLimit != -1L && size > containerLengthLimit) {
            throw ProtocolException("Container size limit exceeded")
        }
        return MapMetadata(keyTypeId, valueTypeId, size)
    }

    override suspend fun readMapEnd() {
    }

    override suspend fun readListBegin(): ListMetadata {
        val elementTypeId = readByte()
        val size = readI32()
        if (containerLengthLimit != -1L && size > containerLengthLimit) {
            throw ProtocolException("Container size limit exceeded")
        }
        return ListMetadata(elementTypeId, size)
    }

    override suspend fun readListEnd() {
    }

    override suspend fun readSetBegin(): SetMetadata {
        val elementTypeId = readByte()
        val size = readI32()
        if (containerLengthLimit != -1L && size > containerLengthLimit) {
            throw ProtocolException("Container size limit exceeded")
        }
        return SetMetadata(elementTypeId, size)
    }

    override suspend fun readSetEnd() {
    }

    override suspend fun readBool(): Boolean {
        return readByte().toInt() == 1
    }

    override suspend fun readByte(): Byte {
        readFully(buffer, 1)
        return buffer[0]
    }

    override suspend fun readI16(): Short {
        readFully(buffer, 2)
        return (((buffer[0].toInt() and 0xFF) shl 8)
                or (buffer[1].toInt() and 0xFF)).toShort()
    }

    override suspend fun readI32(): Int {
        readFully(buffer, 4)
        return (((buffer[0].toInt() and 0xFF) shl 24)
                or ((buffer[1].toInt() and 0xFF) shl 16)
                or ((buffer[2].toInt() and 0xFF) shl 8)
                or (buffer[3].toInt() and 0xFF))
    }

    override suspend fun readI64(): Long {
        readFully(buffer, 8)
        return (((buffer[0].toLong() and 0xFFL) shl 56)
                or ((buffer[1].toLong() and 0xFFL) shl 48)
                or ((buffer[2].toLong() and 0xFFL) shl 40)
                or ((buffer[3].toLong() and 0xFFL) shl 32)
                or ((buffer[4].toLong() and 0xFFL) shl 24)
                or ((buffer[5].toLong() and 0xFFL) shl 16)
                or ((buffer[6].toLong() and 0xFFL) shl 8)
                or ((buffer[7].toLong() and 0xFFL)))
    }

    override suspend fun readDouble(): Double {
        return Double.fromBits(readI64())
    }

    override suspend fun readString(): String {
        val sizeInBytes = readI32()
        if (stringLengthLimit != -1L && sizeInBytes > stringLengthLimit) {
            throw ProtocolException("String size limit exceeded")
        }
        return readStringWithSize(sizeInBytes)
    }

    override suspend fun readBinary(): ByteString {
        val sizeInBytes = readI32()
        if (stringLengthLimit != -1L && sizeInBytes > stringLengthLimit) {
            throw ProtocolException("Binary size limit exceeded")
        }
        val data = ByteArray(sizeInBytes)
        readFully(data, data.size)
        return data.toByteString()
    }

    private suspend fun readStringWithSize(size: Int): String {
        val encoded = ByteArray(size)
        readFully(encoded, size)
        return encoded.decodeToString()
    }

    private suspend fun readFully(buffer: ByteArray, count: Int) {
        var toRead = count
        var offset = 0
        while (toRead > 0) {
            val read = transport.read(buffer, offset, toRead)
            if (read == -1) {
                throw EOFException("Expected $count bytes; got $offset")
            }
            toRead -= read
            offset += read
        }
    }

    companion object {
        private const val VERSION_MASK = -0x10000
        private const val VERSION_1 = -0x7fff0000
        private val NO_STRUCT = StructMetadata("")
    }
}
