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
package com.bendb.thrifty.service

import com.bendb.thrifty.Struct
import com.bendb.thrifty.ThriftException
import com.bendb.thrifty.ThriftException.Companion.read
import com.bendb.thrifty.protocol.Protocol
import kotlinx.atomicfu.atomic
import okio.Closeable
import okio.IOException

/**
 * Implements a basic service client that executes methods synchronously.
 *
 * Unlike the Apache implementation, there is no presumption made here about framed encoding
 * at the transport level.  If your backend requires framing, be sure to
 * configure your [Protocol] and [com.bendb.thrifty.transport.Transport]
 * objects appropriately.
 */
open class ClientBase protected constructor(private val protocol: Protocol) : Closeable {
    /**
     * A sequence ID generator; contains the most-recently-used
     * sequence ID (or zero, if no calls have been made).
     */
    private val seqId = atomic(0)

    /**
     * A flag indicating whether the client is active and connected.
     */
    protected val running = atomic(true)

    /**
     * When invoked by a derived instance, sends the given call to the server.
     *
     * @param methodCall the remote method call to be invoked
     * @return the result of the method call
     */
    protected suspend fun <T> execute(methodCall: MethodCall<T>): T? {
        check(running.value) { "Cannot write to a closed service client" }
        return try {
            invokeRequest(methodCall)
        } catch (e: ServerException) {
            throw e.thriftException
        }
    }

    /**
     * Closes this service client and the underlying protocol.
     *
     * Subclasses that override this method need to set [running] to false and call [closeProtocol].
     */
    @Throws(IOException::class)
    override fun close() {
        if (!running.compareAndSet(true, false)) {
            return
        }
        closeProtocol()
    }

    fun closeProtocol() {
        try {
            protocol.close()
        } catch (ignored: IOException) {
            // nope
        }
    }

    /**
     * Send the given call to the server.
     *
     * @param call the remote method call to be invoked
     * @return the result of the method call
     * @throws ServerException wrapper around [ThriftException]. Callers should catch and unwrap this.
     * @throws IOException from the protocol
     * @throws Exception exception received from server implements [com.bendb.thrifty.Struct]
     */
    @Throws(Exception::class)
    suspend fun <T> invokeRequest(call: MethodCall<T>): T {
        val isOneWay = call.callTypeId == TMessageType.ONEWAY
        val sid = seqId.incrementAndGet()
        protocol.writeMessageBegin(call.name, call.callTypeId, sid)
        call.send(protocol)
        protocol.writeMessageEnd()
        protocol.flush()
        if (isOneWay) {
            // No response will be received
            @Suppress("UNCHECKED_CAST")
            return Unit as T
        }
        val metadata = protocol.readMessageBegin()
        if (metadata.seqId != sid) {
            throw ThriftException(
                    ThriftException.Kind.BAD_SEQUENCE_ID,
                    "Unrecognized sequence ID")
        }
        if (metadata.type == TMessageType.EXCEPTION) {
            val e = read(protocol)
            protocol.readMessageEnd()
            throw ServerException(e)
        } else if (metadata.type != TMessageType.REPLY) {
            throw ThriftException(
                    ThriftException.Kind.INVALID_MESSAGE_TYPE,
                    "Invalid message type: " + metadata.type)
        }
        if (metadata.seqId != seqId.value) {
            throw ThriftException(
                    ThriftException.Kind.BAD_SEQUENCE_ID,
                    "Out-of-order response")
        }
        if (metadata.name != call.name) {
            throw ThriftException(
                    ThriftException.Kind.WRONG_METHOD_NAME,
                    "Unexpected method name in reply; expected " + call.name
                            + " but received " + metadata.name)
        }
        return try {
            val result = call.receive(protocol, metadata)
            protocol.readMessageEnd()
            result
        } catch (e: Exception) {
            if (e is Struct) {
                // Business as usual
                protocol.readMessageEnd()
            }
            throw e
        }
    }

    internal class ServerException(val thriftException: ThriftException) : Exception()
}
