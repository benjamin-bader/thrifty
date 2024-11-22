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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
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
 * @param listener a callback object to receive client-level events.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class AsyncClientBase protected constructor(
    // todo: name of the service
    protocol: Protocol,
    listener: Listener
) : ClientBase(protocol), Closeable {
    private val dispatcher = Dispatchers.Default.limitedParallelism(parallelism = 1)
    private val job = SupervisorJob()
    private val scope = CoroutineScope(dispatcher + job)

    /**
     * Exposes important events in the client's lifecycle.
     */
    interface Listener {
        /**
         * Invoked when the client connection has been closed.
         *
         * After invocation, the client is no longer usable.  All subsequent
         * method call attempts will result in an immediate exception on the
         * calling thread.
         */
        fun onTransportClosed()

        /**
         * Invoked when a client-level error has occurred.
         *
         * This generally indicates a connectivity or protocol error,
         * and is distinct from errors returned as part of normal service
         * operation.
         *
         * The client is guaranteed to have been closed and shut down
         * by the time this method is invoked.
         *
         * @param error the throwable instance representing the error.
         */
        fun onError(error: Throwable)
    }

    protected suspend fun <T> executeMethodCall(methodCall: MethodCall<T>): T {
        check(running.value) { "Cannot write to a closed service client" }

        return try {
            scope.async { invokeRequest(methodCall) }.await()
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

    override fun close() {
        closeWithException(null)
    }

    private fun closeWithException(cause: Throwable?) {
        if (!running.compareAndSet(true, false)) {
            return
        }

        scope.cancel()
        closeProtocol()
    }
}
