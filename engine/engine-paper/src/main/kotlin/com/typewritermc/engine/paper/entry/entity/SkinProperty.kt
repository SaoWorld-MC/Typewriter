package com.typewritermc.engine.paper.entry.entity

import com.google.common.cache.CacheBuilder
import com.typewritermc.core.extension.Initializable
import com.typewritermc.core.utils.UntickedAsync
import com.typewritermc.engine.paper.entry.entries.EntityProperty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.future.await
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.koin.java.KoinJavaComponent
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class SkinProperty(
    val texture: String = "",
    val signature: String = "",
) : EntityProperty {
    companion object : SinglePropertyCollectorSupplier<SkinProperty>(SkinProperty::class)
}

class PlayerSkinCache : Initializable {
    private val cache = CacheBuilder.newBuilder()
        .expireAfterAccess(10, java.util.concurrent.TimeUnit.MINUTES)
        .build<UUID, SkinProperty>()
    private val jobs = ConcurrentHashMap<UUID, Job>()

    operator fun get(playerId: UUID): SkinProperty {
        cache.getIfPresent(playerId)?.let { return it }

        val onlinePlayer = Bukkit.getPlayer(playerId)
        if (onlinePlayer != null) {
            val textures = onlinePlayer.playerProfile.properties.firstOrNull { it.name == "textures" }
            if (textures != null) {
                val skin = SkinProperty(textures.value, textures.signature ?: "")
                cache.put(playerId, skin)
                return skin
            }
        }

        requestSkin(playerId)
        return SkinProperty()
    }

    /**
     * Waits for the player's signed texture property without blocking the server thread.
     */
    suspend fun load(playerId: UUID): SkinProperty {
        cache.getIfPresent(playerId)?.let { return it }
        requestSkin(playerId).join()
        return cache.getIfPresent(playerId) ?: SkinProperty()
    }

    private fun requestSkin(playerId: UUID): Job {
        jobs[playerId]?.let { return it }

        val newJob = CoroutineScope(Dispatchers.UntickedAsync).launch(start = CoroutineStart.LAZY) {
            val offlinePlayer = Bukkit.getOfflinePlayer(playerId)
            var profile = offlinePlayer.playerProfile
            if (!profile.hasTextures()) {
                profile = profile.update().await()
            }

            val textures = profile.properties.firstOrNull { it.name == "textures" } ?: return@launch
            val skin = SkinProperty(textures.value, textures.signature ?: "")
            cache.put(playerId, skin)
        }
        val job = jobs.putIfAbsent(playerId, newJob) ?: newJob
        if (job === newJob) {
            newJob.invokeOnCompletion { jobs.remove(playerId, newJob) }
            newJob.start()
        } else {
            newJob.cancel()
        }
        return job
    }

    override suspend fun initialize() {}

    override suspend fun shutdown() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        cache.invalidateAll()
    }
}

val OfflinePlayer.skin: SkinProperty
    get() = KoinJavaComponent.get<PlayerSkinCache>(PlayerSkinCache::class.java)[uniqueId]

/**
 * Resolves the player's signed texture property when it is not already cached.
 */
suspend fun OfflinePlayer.loadSkin(): SkinProperty =
    KoinJavaComponent.get<PlayerSkinCache>(PlayerSkinCache::class.java).load(uniqueId)
