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
package com.bendb.thrifty.transport

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.SocketOptions
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.time.Duration

// ktor doesn't have a common tls() method.  If we want to manager the tls() call,
// we need to do it with expect/actual to reach the platform-specific APIs.
expect suspend fun Socket.establishTls(): Socket

class SocketTransport internal constructor(
    builder: Builder
) : Transport {
    class Builder(
        internal val host: String,
        internal val port: Int,
    ) {
        internal var tlsEnabled: Boolean = false
        internal var dispatcher: CoroutineDispatcher = Dispatchers.IO
        internal var readTimeout: Duration = Duration.INFINITE

        fun readTimeout(timeout: Duration): Builder = apply { readTimeout = timeout }

        fun enableTls(enableTls: Boolean): Builder = apply { tlsEnabled = enableTls }

        fun dispatcher(dispatcher: CoroutineDispatcher): Builder = apply { this.dispatcher = dispatcher }

        fun build(): SocketTransport = SocketTransport(this)
    }

    private val host = builder.host
    private val port = builder.port
    private val tlsEnabled = builder.tlsEnabled
    private val readTimeout = builder.readTimeout

    private lateinit var selectorManager: SelectorManager
    private lateinit var socket: Socket
    private lateinit var readChannel: ByteReadChannel
    private lateinit var writeChannel: ByteWriteChannel

    override suspend fun read(buffer: ByteArray, offset: Int, count: Int): Int {
        require(offset >= 0) { "offset cannot be negative" }
        require(count >= 0) { "count cannot be negative" }
        require(offset + count <= buffer.size) { "offset + count cannot exceed buffer size" }

        readChannel.readFully(buffer, offset, count)
        return count
    }

    override suspend fun write(data: ByteArray) {
        writeChannel.writeFully(data)
    }

    override suspend fun write(buffer: ByteArray, offset: Int, count: Int) {
        writeChannel.writeFully(buffer, offset, count)
    }

    override suspend fun flush() {
        writeChannel.flush()
    }

    suspend fun connect() {
        selectorManager = SelectorManager(Dispatchers.IO)
        socket = aSocket(selectorManager)
            .tcp()
            .connect(host, port) {
                keepAlive = true
                noDelay = true
                reuseAddress = false
                reusePort = false
                socketTimeout = readTimeout.inWholeMilliseconds
            }

        if (tlsEnabled) {
            socket = socket.establishTls()
        }
        readChannel = socket.openReadChannel()
        writeChannel = socket.openWriteChannel(autoFlush = false)
    }

    override fun close() {
        writeChannel.close(null)
        socket.close()
        selectorManager.close()
    }
}
