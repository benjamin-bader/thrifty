package com.bendb.thrifty.transport

expect class SocketTransport internal constructor(
    builder: Builder
) : Transport {
    class Builder(host: String, port: Int) {
        /**
         * The number of milliseconds to wait for a connection to be established.
         */
        fun connectTimeout(connectTimeout: Int): Builder

        /**
         * The number of milliseconds a read operation should wait for completion.
         */
        fun readTimeout(readTimeout: Int): Builder

        /**
         * Enable TLS for this connection.
         */
        fun enableTls(enableTls: Boolean): Builder

        fun build(): SocketTransport
    }

    @Throws(okio.IOException::class)
    fun connect()
}
