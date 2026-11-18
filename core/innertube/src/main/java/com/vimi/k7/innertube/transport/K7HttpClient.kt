package com.vimi.k7.innertube.transport

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

/**
 * Global singleton HTTP infrastructure for K7.
 *
 * Provides a shared [ConnectionPool] and dispatcher with HTTP/2 enabled across the entire
 * application, ensuring TLS socket reuse to `*.googlevideo.com` and `youtubei.googleapis.com`
 * to eliminate repeated TLS handshake latency during streaming and downloads.
 */
object K7HttpClient {

    val connectionPool: ConnectionPool = ConnectionPool(
        maxIdleConnections = 8,
        keepAliveDuration = 3,
        TimeUnit.MINUTES,
    )

    val dispatcher: Dispatcher = Dispatcher().apply {
        maxRequests = 32
        maxRequestsPerHost = 8
    }

    /**
     * Shared high-performance HTTP client for data transfer (downloads and streaming).
     */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(connectionPool)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
