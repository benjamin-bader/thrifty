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

import okio.EOFException

/**
 * A transport decorator that reads from and writes to the underlying transport
 * in length-prefixed frames.  Used when the server is using a non-blocking
 * implementation, which currently requires such framing.
 */
class FramedTransport(
        private val inner: Transport
) : Transport {
    // Read state
    private var remainingBytes = 0

    // Write state
    private var pendingWrite: SimpleBuffer? = null

    override fun close() {
        inner.close()
        pendingWrite = null
    }

    override suspend fun read(buffer: ByteArray, offset: Int, count: Int): Int {
        while (remainingBytes <= 0) {
            readHeader()
        }
        val toRead = count.coerceAtMost(remainingBytes)
        val numRead = inner.read(buffer, offset, toRead)
        remainingBytes -= numRead
        return numRead
    }

    private suspend fun readHeader() {
        val headerBytes = ByteArray(4)
        var numRead = 0
        while (numRead < headerBytes.size) {
            val n = inner.read(headerBytes, numRead, headerBytes.size - numRead)
            if (n == -1) {
                throw EOFException()
            }
            numRead += n
        }
        remainingBytes = (
                   ((headerBytes[0].toInt() and 0xFF) shl 24)
                or ((headerBytes[1].toInt() and 0xFF) shl 16)
                or ((headerBytes[2].toInt() and 0xFF) shl 8)
                or ( headerBytes[3].toInt() and 0xFF))
    }

    override suspend fun write(buffer: ByteArray, offset: Int, count: Int) {
        if (pendingWrite == null) {
            pendingWrite = SimpleBuffer(count)
        }
        pendingWrite!!.write(buffer, offset, count)
    }

    override suspend fun flush() {
        val write = pendingWrite ?: return
        val size = write.size
        if (size == 0) {
            return
        }

        write.buf[0] = ((size shr 24) and 0xFF).toByte()
        write.buf[1] = ((size shr 16) and 0xFF).toByte()
        write.buf[2] = ((size shr 8)  and 0xFF).toByte()
        write.buf[3] = ( size         and 0xFF).toByte()
        inner.write(write.buf, 0, size + SimpleBuffer.HEADER_BUF_SIZE)
        inner.flush()
        write.reset()
    }

    private class SimpleBuffer(count: Int = 32) {
        var buf: ByteArray = ByteArray(count.coerceAtLeast(32 + HEADER_BUF_SIZE))
        var size: Int = 0

        private val pos: Int
            get() = size + HEADER_BUF_SIZE

        fun write(buffer: ByteArray, offset: Int, count: Int) {
            if (size + HEADER_BUF_SIZE + count > buf.size) {
                buf = buf.copyOf(nextPowerOfTwo(buf.size + count + HEADER_BUF_SIZE))
            }
            buffer.copyInto(
                    destination = buf,
                    destinationOffset = pos,
                    startIndex = offset,
                    endIndex = offset + count)
            size += count
        }

        fun reset() {
            buf = ByteArray(32 + HEADER_BUF_SIZE)
            size = 0
        }

        private fun nextPowerOfTwo(num: Int): Int {
            var n = num - 1
            n = n or (n ushr 1)
            n = n or (n ushr 2)
            n = n or (n ushr 4)
            n = n or (n ushr 8)
            n = n or (n ushr 16)
            return n + 1
        }

        companion object {
            const val HEADER_BUF_SIZE = 4
        }
    }
}
