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

import com.bendb.thrifty.transport.Transport
import okio.Buffer
import okio.ByteString

/**
 * A protocol that maps Thrift data to idiomatic JSON.
 *
 * "Idiomatic" here means that structs map to JSON maps, with field names
 * for keys.  Field tags are not included, and precise type information is not
 * preserved.  For this reason, SimpleJsonProtocol *does not support round-
 * tripping* - it is write-only.
 *
 * Note that, as of the initial release, this Protocol does not guarantee
 * that all emitted data is strictly valid JSON.  In particular, map keys are
 * not guaranteed to to be strings.
 */
class SimpleJsonProtocol(transport: Transport?) : BaseProtocol(transport!!) {
    /**
     * Indicates how [binary][ByteString] data is serialized when
     * written as JSON.
     */
    enum class BinaryOutputMode {
        /**
         * Write binary data as a hex-encoded string.
         */
        HEX,

        /**
         * Write binary data as a base-64-encoded string.
         */
        BASE_64,

        /**
         * Write binary data using Unicode escape syntax.
         */
        UNICODE
    }

    private open class WriteContext {
        open suspend fun beforeWrite() {
        }

        open fun onPop() {
            // Fine
        }
    }

    private inner class ListWriteContext : WriteContext() {
        private var hasWritten = false
        override suspend fun beforeWrite() {
            if (hasWritten) {
                transport.write(COMMA)
            } else {
                hasWritten = true
            }
        }
    }

    private inner class MapWriteContext : WriteContext() {
        private var hasWritten = false
        private var mode = MODE_KEY

        override suspend fun beforeWrite() {
            if (hasWritten) {
                if (mode == MODE_KEY) {
                    transport.write(COMMA)
                } else {
                    transport.write(COLON)
                }
            } else {
                hasWritten = true
            }
            mode = !mode
        }

        override fun onPop() {
            if (mode == MODE_VALUE) {
                throw ProtocolException("Incomplete JSON map, expected a value")
            }
        }
    }

    companion object {
        private const val MODE_KEY = false
        private const val MODE_VALUE = true

        private val ESCAPES: Array<CharArray?> = arrayOfNulls(128)
        private val TRUE =
            byteArrayOf('t'.code.toByte(), 'r'.code.toByte(), 'u'.code.toByte(), 'e'.code.toByte())
        private val FALSE = byteArrayOf(
            'f'.code.toByte(),
            'a'.code.toByte(),
            'l'.code.toByte(),
            's'.code.toByte(),
            'e'.code.toByte()
        )
        private val COMMA = byteArrayOf(','.code.toByte())
        private val COLON: ByteArray = byteArrayOf(':'.code.toByte())
        private val LBRACKET = byteArrayOf('['.code.toByte())
        private val RBRACKET = byteArrayOf(']'.code.toByte())
        private val LBRACE = byteArrayOf('{'.code.toByte())
        private val RBRACE = byteArrayOf('}'.code.toByte())

        init {
            for (i in 0..31) {
                // Control chars must be escaped
                val chars = CharArray(6)
                chars[0] = '\\'
                chars[1] = 'u'
                chars[2] = '0'
                chars[3] = '0'
                chars[4] = "0123456789abcdef"[(i shr 4) and 0xF]
                chars[5] = "0123456789abcdef"[i and 0xF]
                ESCAPES[i] = chars
            }
            ESCAPES['\\'.code] = charArrayOf('\\', '\\')
            ESCAPES['\"'.code] = charArrayOf('\\', '"')
            ESCAPES['\b'.code] = charArrayOf('\\', 'b')
            ESCAPES['\u000C'.code] = charArrayOf('\\', 'f')
            ESCAPES['\r'.code] = charArrayOf('\\', 'r')
            ESCAPES['\n'.code] = charArrayOf('\\', 'n')
            ESCAPES['\t'.code] = charArrayOf('\\', 't')
        }
    }

    private val defaultWriteContext: WriteContext = object : WriteContext() {
        override suspend fun beforeWrite() {
            // nothing
        }
    }
    private val writeStack = ArrayDeque<WriteContext>()
    private var binaryOutputMode = BinaryOutputMode.HEX
    fun withBinaryOutputMode(mode: BinaryOutputMode): SimpleJsonProtocol {
        binaryOutputMode = mode
        return this
    }

    override suspend fun writeMessageBegin(name: String, typeId: Byte, seqId: Int) {
        writeMapBegin(typeId, typeId, 0) // values are ignored here
        writeString("name")
        writeString(name)
        writeString("value")
    }

    override suspend fun writeMessageEnd() {
        writeMapEnd()
    }

    override suspend fun writeStructBegin(structName: String) {
        writeContext().beforeWrite()
        pushWriteContext(MapWriteContext())
        transport.write(LBRACE)
        writeString("__thriftStruct")
        writeString(structName)
    }

    override suspend fun writeStructEnd() {
        transport.write(RBRACE)
        popWriteContext()
    }

    override suspend fun writeFieldBegin(fieldName: String, fieldId: Int, typeId: Byte) {
        // TODO: assert that we're in map context
        writeString(fieldName)
    }

    override suspend fun writeFieldEnd() {
    }

    override suspend fun writeFieldStop() {
    }

    override suspend fun writeMapBegin(keyTypeId: Byte, valueTypeId: Byte, mapSize: Int) {
        writeContext().beforeWrite()
        pushWriteContext(MapWriteContext())
        transport.write(LBRACE)
    }

    override suspend fun writeMapEnd() {
        transport.write(RBRACE)
        popWriteContext()
    }

    override suspend fun writeListBegin(elementTypeId: Byte, listSize: Int) {
        writeContext().beforeWrite()
        pushWriteContext(ListWriteContext())
        transport.write(LBRACKET)
    }

    override suspend fun writeListEnd() {
        transport.write(RBRACKET)
        popWriteContext()
    }

    override suspend fun writeSetBegin(elementTypeId: Byte, setSize: Int) {
        writeContext().beforeWrite()
        pushWriteContext(ListWriteContext())
        transport.write(LBRACKET)
    }

    override suspend fun writeSetEnd() {
        transport.write(RBRACKET)
        popWriteContext()
    }

    override suspend fun writeBool(b: Boolean) {
        writeContext().beforeWrite()
        transport.write(if (b) TRUE else FALSE)
    }

    override suspend fun writeByte(b: Byte) {
        writeContext().beforeWrite()
        val toWrite = b.toString().encodeToByteArray()
        transport.write(toWrite)
    }

    override suspend fun writeI16(i16: Short) {
        writeContext().beforeWrite()
        transport.write(i16.toString().encodeToByteArray())
    }

    override suspend fun writeI32(i32: Int) {
        writeContext().beforeWrite()
        transport.write(i32.toString().encodeToByteArray())
    }

    override suspend fun writeI64(i64: Long) {
        writeContext().beforeWrite()
        transport.write(i64.toString().encodeToByteArray())
    }

    override suspend fun writeDouble(dub: Double) {
        writeContext().beforeWrite()
        transport.write(dub.toString().encodeToByteArray())
    }

    override suspend fun writeString(str: String) {
        writeContext().beforeWrite()
        val len = str.length
        val buffer = Buffer()
        buffer.writeUtf8CodePoint('"'.code)
        for (i in 0 until len) {
            val c = str[i]
            if (c.code < 128) {
                val maybeEscape = ESCAPES[c.code]
                if (maybeEscape != null) {
                    maybeEscape.forEach { buffer.writeUtf8CodePoint(it.code) } // These are known to be equivalent
                } else {
                    buffer.writeUtf8CodePoint(c.code)
                }
            } else {
                buffer.writeUtf8("$c") // Not sure how to get code points from a string in MPP
            }
        }
        buffer.writeUtf8CodePoint('"'.code)
        val bs = buffer.readByteArray()
        transport.write(bs, 0, bs.size)
    }

    override suspend fun writeBinary(buf: ByteString) {
        val out = when (binaryOutputMode) {
            BinaryOutputMode.HEX -> buf.hex()
            BinaryOutputMode.BASE_64 -> buf.base64()
            BinaryOutputMode.UNICODE -> buf.utf8()
        }
        writeString(out)
    }

    private fun pushWriteContext(context: WriteContext) {
        writeStack.addFirst(context)
    }

    private fun writeContext(): WriteContext {
        var top = writeStack.firstOrNull()
        if (top == null) {
            top = defaultWriteContext
        }
        return top
    }

    private fun popWriteContext() {
        val context = writeStack.removeFirstOrNull()
        if (context == null) {
            throw ProtocolException("stack underflow")
        } else {
            context.onPop()
        }
    }

    override suspend fun readMessageBegin(): MessageMetadata {
        throw UnsupportedOperationException()
    }

    override suspend fun readMessageEnd() {
        throw UnsupportedOperationException()
    }

    override suspend fun readStructBegin(): StructMetadata {
        throw UnsupportedOperationException()
    }

    override suspend fun readStructEnd() {
        throw UnsupportedOperationException()
    }

    override suspend fun readFieldBegin(): FieldMetadata {
        throw UnsupportedOperationException()
    }

    override suspend fun readFieldEnd() {
        throw UnsupportedOperationException()
    }

    override suspend fun readMapBegin(): MapMetadata {
        throw UnsupportedOperationException()
    }

    override suspend fun readMapEnd() {
        throw UnsupportedOperationException()
    }

    override suspend fun readListBegin(): ListMetadata {
        throw UnsupportedOperationException()
    }

    override suspend fun readListEnd() {
        throw UnsupportedOperationException()
    }

    override suspend fun readSetBegin(): SetMetadata {
        throw UnsupportedOperationException()
    }

    override suspend fun readSetEnd() {
        throw UnsupportedOperationException()
    }

    override suspend fun readBool(): Boolean {
        throw UnsupportedOperationException()
    }

    override suspend fun readByte(): Byte {
        throw UnsupportedOperationException()
    }

    override suspend fun readI16(): Short {
        throw UnsupportedOperationException()
    }

    override suspend fun readI32(): Int {
        throw UnsupportedOperationException()
    }

    override suspend fun readI64(): Long {
        throw UnsupportedOperationException()
    }

    override suspend fun readDouble(): Double {
        throw UnsupportedOperationException()
    }

    override suspend fun readString(): String {
        throw UnsupportedOperationException()
    }

    override suspend fun readBinary(): ByteString {
        throw UnsupportedOperationException()
    }
}
