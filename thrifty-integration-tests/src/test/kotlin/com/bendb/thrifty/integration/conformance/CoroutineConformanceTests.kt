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
package com.bendb.thrifty.integration.conformance

import com.bendb.thrifty.ThriftException
import com.bendb.thrifty.binaryProtocol
import com.bendb.thrifty.compactProtocol
import com.bendb.thrifty.integration.kgen.coro.Bonk
import com.bendb.thrifty.integration.kgen.coro.HasUnion
import com.bendb.thrifty.integration.kgen.coro.Insanity
import com.bendb.thrifty.integration.kgen.coro.NonEmptyUnion
import com.bendb.thrifty.integration.kgen.coro.Numberz
import com.bendb.thrifty.integration.kgen.coro.ThriftTestClient
import com.bendb.thrifty.integration.kgen.coro.UnionWithDefault
import com.bendb.thrifty.integration.kgen.coro.Xception
import com.bendb.thrifty.integration.kgen.coro.Xception2
import com.bendb.thrifty.integration.kgen.coro.Xtruct
import com.bendb.thrifty.integration.kgen.coro.Xtruct2
import com.bendb.thrifty.jsonProtocol
import com.bendb.thrifty.protocol.Protocol
import com.bendb.thrifty.service.AsyncClientBase
import com.bendb.thrifty.testing.ServerConfig
import com.bendb.thrifty.testing.ServerProtocol
import com.bendb.thrifty.testing.ServerTransport
import com.bendb.thrifty.testing.TestServer
import com.bendb.thrifty.transport.FramedTransport
import com.bendb.thrifty.transport.SocketTransport
import com.bendb.thrifty.transport.Transport
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@ServerConfig(transport = ServerTransport.BLOCKING, protocol = ServerProtocol.BINARY)
class BinaryCoroutineConformanceTest : CoroutineConformanceTests()

@ServerConfig(transport = ServerTransport.BLOCKING, protocol = ServerProtocol.COMPACT)
class CompactCoroutineConformanceTest : CoroutineConformanceTests()

@ServerConfig(transport = ServerTransport.BLOCKING, protocol = ServerProtocol.JSON)
class JsonCoroutineConformanceTest : CoroutineConformanceTests()

@ServerConfig(transport = ServerTransport.NON_BLOCKING, protocol = ServerProtocol.BINARY)
class NonblockingBinaryCoroutineConformanceTest : CoroutineConformanceTests()

@ServerConfig(transport = ServerTransport.NON_BLOCKING, protocol = ServerProtocol.COMPACT)
class NonblockingCompactCoroutineConformanceTest : CoroutineConformanceTests()

@ServerConfig(transport = ServerTransport.NON_BLOCKING, protocol = ServerProtocol.JSON)
class NonblockingJsonCoroutineConformanceTest : CoroutineConformanceTests()

/**
 * A test of auto-generated service code for the standard ThriftTest
 * service.
 *
 * Conformance is checked by roundtripping requests to a local server that
 * is run on the official Apache Thrift Java codebase.  The test server has
 * an implementation of ThriftTest methods with semantics as described in the
 * .thrift file itself and in the Apache Thrift git repo, along with Java code
 * generated by their compiler.
 */
abstract class CoroutineConformanceTests {
    companion object {
        /**
         * An Apache Thrift server that is started anew for each test.
         *
         * The server's transport and protocols are configured based
         * on values returned by the abstract methods
         * [.getServerProtocol] and [.getServerTransport].
         */
        @RegisterExtension
        @JvmField
        val testServer: TestServer = TestServer()

        lateinit var transport: Transport
        lateinit var protocol: Protocol
        lateinit var client: ThriftTestClient

        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            val port = testServer.port()
            val transport = SocketTransport.Builder("localhost", port)
                .readTimeout(2000)
                .build()

            runTest { transport.connect() }

            this.transport = decorateTransport(transport)
            this.protocol = createProtocol(this.transport)
            this.client = ThriftTestClient(protocol, object : AsyncClientBase.Listener {
                    override fun onTransportClosed() {

                    }

                    override fun onError(error: Throwable) {
                        throw AssertionError(error)
                    }
                })
        }

        /**
         * When overridden in a derived class, wraps the given transport
         * in a decorator, e.g. a framed transport.
         */
        private fun decorateTransport(transport: Transport): Transport {
            return when (testServer.transport) {
                ServerTransport.NON_BLOCKING -> FramedTransport(transport)
                else -> transport
            }
        }

        private fun createProtocol(transport: Transport): Protocol {
            return when (testServer.protocol!!) {
                ServerProtocol.BINARY -> transport.binaryProtocol()
                ServerProtocol.COMPACT -> transport.compactProtocol()
                ServerProtocol.JSON -> transport.jsonProtocol()
            }
        }

        @AfterAll
        @JvmStatic
        fun afterAll() {
            client.close()
            protocol.close()
            transport.close()
            testServer.close()
        }
    }

    @Test
    fun testVoid() = runTest {
        client.testVoid() shouldBe Unit
    }

    @Test
    fun testBool() = runTest {
        client.testBool(true) shouldBe true
    }

    @Test
    fun testByte() = runTest {
        client.testByte(200.toByte()) shouldBe 200.toByte()
    }

    @Test
    fun testI32() = runTest {
        client.testI32(404) shouldBe 404
    }

    @Test
    fun testI64() = runTest {
        client.testI64(Long.MAX_VALUE) shouldBe Long.MAX_VALUE
    }

    @Test
    fun testDouble() = runTest {
        client.testDouble(Math.PI) shouldBe Math.PI
    }

    @Test
    fun testBinary() = runTest {
        val binary = "Peace on Earth and Thrift for all mankind".encodeUtf8()

        client.testBinary(binary) shouldBe binary
    }

    @Test
    fun testStruct() = runTest {
        val xtruct = Xtruct(
                byte_thing = 1.toByte(),
                i32_thing = 2,
                i64_thing = 3L,
                string_thing = "foo",
                bool_thing = null,
                double_thing = null
        )

        client.testStruct(xtruct) shouldBe xtruct
    }

    @Test
    fun testNest() = runTest {
        val xtruct = Xtruct(
                byte_thing = 1.toByte(),
                i32_thing = 2,
                i64_thing = 3L,
                string_thing = "foo",
                bool_thing = null,
                double_thing = null
        )

        val nest = Xtruct2(
                byte_thing = 4.toByte(),
                i32_thing = 5,
                struct_thing = xtruct
        )

        client.testNest(nest) shouldBe nest
    }

    @Test
    fun testMap() = runTest {
        val argument = mapOf(1 to 2, 3 to 4, 7 to 8)

        client.testMap(argument) shouldBe argument
    }

    @Test
    fun testStringMap() = runTest {
        val argument = mapOf(
                "foo\no" to "bar",
                "baz" to "qu\rux",
                "one" to "more"
        )

        client.testStringMap(argument) shouldBe argument
    }

    @Test
    fun testSet() = runTest {
        val set = setOf(1, 2, 3, 4, 5)

        client.testSet(set) shouldBe set
    }

    @Test
    fun testList() = runTest {
        val list = listOf(10, 9, 8, 7, 6, 5, 4, 3, 2, 1)

        client.testList(list) shouldBe list
    }

    @Test
    fun testEnum() = runTest {
        val argument = Numberz.EIGHT

        client.testEnum(argument) shouldBe argument
    }

    @Test
    fun testTypedef() = runTest {
        client.testTypedef(Long.MIN_VALUE) shouldBe Long.MIN_VALUE
    }

    @Test
    fun testMapMap() = runTest {
        client.testMapMap(Integer.MAX_VALUE) shouldBe mapOf(
                -4 to mapOf(
                        -4 to -4,
                        -3 to -3,
                        -2 to -2,
                        -1 to -1
                ),

                4 to mapOf(
                        1 to 1,
                        2 to 2,
                        3 to 3,
                        4 to 4
                )
        )
    }

    @Test
    fun testInsanity() = runTest {
        val empty = Insanity(null, null)
        val argument = Insanity(
                userMap = mapOf(Numberz.ONE to 10L, Numberz.TWO to 20L, Numberz.THREE to 40L),
                xtructs = listOf(
                        Xtruct(
                                byte_thing = 18.toByte(),
                                i32_thing = 37,
                                i64_thing = 101L,
                                string_thing = "what",
                                bool_thing = null,
                                double_thing = null)))

        val expected = mapOf(
                1L to mapOf(Numberz.TWO to argument, Numberz.THREE to argument),
                2L to mapOf(Numberz.SIX to empty)
        )

        client.testInsanity(argument) shouldBe expected
    }

    @Test
    fun testMulti() = runTest {
        val expected = Xtruct(
                string_thing = "Hello2",
                byte_thing = 9.toByte(),
                i32_thing = 11,
                i64_thing = 13L,
                bool_thing = null,
                double_thing = null
        )

        val result = client.testMulti(
                arg0 = 9.toByte(),
                arg1 = 11,
                arg2 = 13L,
                arg3 = mapOf(10.toShort() to "Hello"),
                arg4 = Numberz.THREE,
                arg5 = 5L)

        result shouldBe expected
    }

    @Test
    fun testExceptionNormalError() = runTest {
        try {
            client.testException("Xception")
            fail("Expected an Xception")
        } catch (e: Xception) {
            e.errorCode shouldBe 1001
            e.message_ shouldBe "Xception"
        }
    }

    @Test
    fun testExceptionInternalError() = runTest {
        try {
            client.testException("TException")
            fail("Expected a ThriftException")
        } catch (e: ThriftException) {
            e.kind shouldBe ThriftException.Kind.INTERNAL_ERROR
        }
    }

    @Test
    fun testMultiExceptionNoError() = runTest {
        val (string_thing) = client.testMultiException("Normal", "Hi there")

        // Note: We aren't asserting against an expected value because the members
        //       of the result are unspecified besides 'string_thing', and Thrift
        //       implementations differ on whether to return unset primitive values,
        //       depending on options set during codegen.
        string_thing shouldBe "Hi there"
    }

    @Test
    fun testMultiExceptionErrorOne() = runTest {
        val expected = Xception(
                errorCode = 1001,
                message_ = "This is an Xception")

        try {
            client.testMultiException("Xception", "nope")
            fail("Expected an Xception")
        } catch (e: Xception) {
            e shouldBe expected
        }
    }

    @Test
    fun testMultiExceptionErrorTwo() = runTest {
        try {
            client.testMultiException("Xception2", "nope")
            fail("Expected an Xception2")
        } catch (e: Xception2) {
            // Note: We aren't asserting against an expected value because the members
            //       of 'struct_thing' are unspecified besides 'string_thing', and Thrift
            //       implementations differ on whether to return unset primitive values,
            //       depending on options set during codegen.
            e.errorCode shouldBe 2002
            e.struct_thing?.string_thing shouldBe "This is an Xception2"
        }
    }

    @Test
    fun testUnionArguments() = runTest {
        val bonk = Bonk(message = "foo", type = 42)
        val union = NonEmptyUnion.ABonk(bonk)
        val expected = HasUnion(union)

        client.testUnionArgument(union) shouldBe expected
    }

    @Test
    fun testUnionWithDefault() = runTest {
        val expected = UnionWithDefault.DEFAULT
        client.testUnionWithDefault(UnionWithDefault.DEFAULT) shouldBe expected
    }

    @Test
    fun concurrentAsyncCalls() = runTest {
        val d1 = async { client.testBool(true) shouldBe true }
        val d2 = async { client.testByte(200.toByte()) shouldBe 200.toByte() }
        val d3 = async { client.testI32(404) shouldBe 404 }
        val d4 = async { client.testI64(Long.MAX_VALUE) shouldBe Long.MAX_VALUE }
        val d5 = async {
            val expected = Xception(errorCode = 1001, message_ = "This is an Xception")
            try {
                client.testMultiException("Xception", "nope")
                fail("Expected an Xception")
            } catch (e: Xception) {
                e shouldBe expected
            }
        }

        awaitAll(d1, d2, d3, d4, d5)
    }

    @Test
    fun oneway() = runTest {
        client.testOneway(secondsToSleep = 0)
    }
}
