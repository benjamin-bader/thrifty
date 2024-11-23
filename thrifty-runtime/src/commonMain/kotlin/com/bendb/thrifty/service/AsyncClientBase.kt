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
import com.bendb.thrifty.protocol.Protocol
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Closeable

/**
 * Implements a basic service client that executes methods asynchronously.
 *
 * Note that, while the client-facing API of this class is callback-based,
 * the implementation itself is **blocking**.  Unlike the Apache
 * implementation, there is no presumption made here about framed encoding
 * at the transport level.  If your backend requires framing, be sure to
 * configure your [Protocol] and [com.bendb.thrifty.transport.Transport]
 * objects appropriately.
 *
 * @param protocol the [Protocol] used to encode/decode requests and responses.
 * @param dispatcher an optional [CoroutineDispatcher] to use for executing requests.
 */
abstract class AsyncClientBase protected constructor(
    protocol: Protocol,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ClientBase(protocol), Closeable {
    private val dispatcher = dispatcher.limitedParallelism(parallelism = 1)
    private val lock = Mutex()

    private val mutableState = MutableStateFlow<ClientState>(ClientState.Running)
    val state: StateFlow<ClientState>
        get() = mutableState

    protected suspend fun <T> executeMethodCall(methodCall: MethodCall<T>): T {
        check(running.value) { "Cannot write to a closed service client" }

        return supervisorScope {
            try {
                withContext(dispatcher) {
                    lock.withLock { invokeRequest(methodCall) }
                }
            } catch (e: ServerException) {
                throw e.thriftException
            } catch (e: Throwable) {
                if (e is Struct) {
                    // In this case, the server has returned an exception that is a Thrift struct
                    // and which therefore is part of the service-method contract.
                    throw e
                }

                // Any other error results in the client being closed
                closeWithException(e)
                throw e
            }
        }
    }

    override fun close() {
        closeWithException(null)
    }

    private fun closeWithException(cause: Throwable?) {
        if (!running.compareAndSet(true, false)) {
            return
        }

        val newState = if (cause == null) ClientState.Closed else ClientState.Failed(cause)
        mutableState.value = newState

        closeProtocol()
    }
}

sealed interface ClientState {
    data object Running : ClientState
    data object Closed : ClientState
    data class Failed(val error: Throwable) : ClientState
}
