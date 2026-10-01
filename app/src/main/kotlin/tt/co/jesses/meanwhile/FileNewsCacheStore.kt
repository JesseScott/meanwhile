package tt.co.jesses.meanwhile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tt.co.jesses.meanwhile.core.CacheEntry
import tt.co.jesses.meanwhile.core.NewsCacheStore
import tt.co.jesses.meanwhile.core.NewsJson
import java.io.File

/** One JSON file per country, so a cached region survives process death and offline launches. */
class FileNewsCacheStore(private val dir: File) : NewsCacheStore {
    override suspend fun get(key: String): CacheEntry? = withContext(Dispatchers.IO) {
        runCatching { NewsJson.decodeFromString<CacheEntry>(file(key).readText()) }.getOrNull()
    }

    override suspend fun put(key: String, entry: CacheEntry) {
        withContext(Dispatchers.IO) {
            runCatching {
                dir.mkdirs()
                file(key).writeText(NewsJson.encodeToString(CacheEntry.serializer(), entry))
            }
        }
    }

    private fun file(key: String) = File(dir, "$key.json")
}
