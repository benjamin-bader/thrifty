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
import com.bendb.thrifty.transport.BufferTransport
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import kotlin.test.Test

class SimpleJsonProtocolTest {
    private val buffer = Buffer()
    private val transport = BufferTransport(buffer)
    private val protocol = SimpleJsonProtocol(transport)

    @Test
    fun emptyJsonString() = runTest {
        protocol.writeString("")
        buffer.readUtf8() shouldBe "\"\""
    }

    @Test
    fun escapesNamedControlChars() = runTest {
        protocol.writeString("\b\u000C\r\n\t")
        buffer.readUtf8() shouldBe "\"\\b\\f\\r\\n\\t\""
    }

    @Test
    fun escapesQuotes() = runTest {
        protocol.writeString("\"")
        buffer.readUtf8() shouldBe "\"\\\"\"" // or, in other words, "\""
    }

    @Test
    fun normalStringIsQuoted() = runTest {
        protocol.writeString("y u no quote me?")
        buffer.readUtf8() shouldBe "\"y u no quote me?\""
    }

    @Test
    fun emptyList() = runTest {
        protocol.writeListBegin(TType.STRING, 0)
        protocol.writeListEnd()
        buffer.readUtf8() shouldBe "[]"
    }

    @Test
    fun listWithOneElement() = runTest {
        protocol.writeListBegin(TType.STRING, 0)
        protocol.writeString("foo")
        protocol.writeListEnd()
        buffer.readUtf8() shouldBe "[\"foo\"]"
    }

    @Test
    fun listWithTwoElements() = runTest {
        protocol.writeListBegin(TType.STRING, 0)
        protocol.writeString("foo")
        protocol.writeString("bar")
        protocol.writeListEnd()
        buffer.readUtf8() shouldBe "[\"foo\",\"bar\"]"
    }

    @Test
    fun emptyMap() = runTest {
        protocol.writeMapBegin(TType.STRING, TType.I32, 0)
        protocol.writeMapEnd()
        buffer.readUtf8() shouldBe "{}"
    }

    @Test
    fun mapWithSingleElement() = runTest {
        protocol.writeMapBegin(TType.STRING, TType.I32, 0)
        protocol.writeString("key1")
        protocol.writeI32(1)
        protocol.writeMapEnd()
        buffer.readUtf8() shouldBe "{\"key1\":1}"
    }

    @Test
    fun mapWithTwoElements() = runTest {
        protocol.writeMapBegin(TType.STRING, TType.I32, 0)
        protocol.writeString("key1")
        protocol.writeI32(1)
        protocol.writeString("key2")
        protocol.writeI32(2)
        protocol.writeMapEnd()
        buffer.readUtf8() shouldBe "{\"key1\":1,\"key2\":2}"
    }

    @Test
    fun listOfMaps() = runTest {
        protocol.writeListBegin(TType.MAP, 2)
        protocol.writeMapBegin(TType.STRING, TType.I32, 1)
        protocol.writeString("1")
        protocol.writeI32(10)
        protocol.writeMapEnd()
        protocol.writeMapBegin(TType.STRING, TType.I32, 1)
        protocol.writeString("2")
        protocol.writeI32(20)
        protocol.writeMapEnd()
        protocol.writeListEnd()
        buffer.readUtf8() shouldBe "[{\"1\":10},{\"2\":20}]"
    }

    @Test
    fun structs() = runTest {
        val xtruct = Xtruct.Builder()
                .byte_thing(1.toByte())
                .double_thing(2.0)
                .i32_thing(3)
                .i64_thing(4L)
                .string_thing("five")
                .build()
        Xtruct.ADAPTER.write(protocol, xtruct)
        buffer.readUtf8() shouldBe "" +
                "{\"__thriftStruct\":\"Xtruct\"," +
                "\"string_thing\":\"five\"," +
                "\"byte_thing\":1," +
                "\"i32_thing\":3," +
                "\"i64_thing\":4," +
                "\"double_thing\":2.0}"
    }

    @Test
    fun hexBinaryOutputMode() = runTest {
        protocol.withBinaryOutputMode(SimpleJsonProtocol.BinaryOutputMode.HEX)
                .writeBinary(byteArrayOf(0, 127, -1).toByteString())
        buffer.readUtf8() shouldBe "\"007fff\""
    }

    @Test
    fun b64BinaryOutputMode() = runTest {
        protocol.withBinaryOutputMode(SimpleJsonProtocol.BinaryOutputMode.BASE_64)
                .writeBinary("foobar".encodeUtf8())
        buffer.readUtf8() shouldBe "\"Zm9vYmFy\""
    }

    @Test
    fun nonAsciiCharacters() = runTest {
        protocol.writeString("测试")
        buffer.readUtf8() shouldBe "\"测试\""
    }
}
