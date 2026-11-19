package com.skarif.kairos.online

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Songs streamed once are kept on disk (up to [MAX_BYTES], least recently played go first),
 * so they replay without a connection and seeking doesn't re-download.
 */
@OptIn(UnstableApi::class)
internal object StreamCache {
    private const val MAX_BYTES = 1024L * 1024 * 1024 // 1 GB

    @Volatile private var instance: SimpleCache? = null

    /** One instance per folder per process, as SimpleCache requires. */
    fun get(context: Context): SimpleCache = instance ?: synchronized(this) {
        instance ?: SimpleCache(
            File(context.applicationContext.cacheDir, "streams"),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            StandaloneDatabaseProvider(context.applicationContext),
        ).also { instance = it }
    }

    /** Fully cached under [key]: the length is known and every byte is on disk. */
    fun isComplete(context: Context, key: String): Boolean {
        val cache = get(context)
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        return length > 0 && cache.isCached(key, 0, length)
    }

    fun clear(context: Context) {
        val cache = get(context)
        cache.keys.toList().forEach { cache.removeResource(it) }
    }
}

/** Sends our "kairos-yt:" Uris to [online] (cached YouTube) and everything else, like local files, to [local]. */
@OptIn(UnstableApi::class)
internal class RoutingDataSource(
    private val scheme: String,
    private val local: DataSource,
    private val online: DataSource,
) : DataSource {
    private var current: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        local.addTransferListener(transferListener)
        online.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = if (dataSpec.uri.scheme == scheme) online else local
        current = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = current!!.read(buffer, offset, length)

    override fun getUri() = current?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()

    override fun close() {
        try { current?.close() } finally { current = null }
    }
}
