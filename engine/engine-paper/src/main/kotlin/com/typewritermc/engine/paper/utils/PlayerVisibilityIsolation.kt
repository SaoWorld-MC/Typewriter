package com.typewritermc.engine.paper.utils

import com.typewritermc.engine.paper.plugin
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlayerVisibilityIsolation(
    private val player: Player,
) {
    private val hiddenOwnerFrom = ConcurrentHashMap.newKeySet<UUID>()
    private val hiddenFromOwner = ConcurrentHashMap.newKeySet<UUID>()
    private var listener: Listener? = null

    fun start() {
        if (listener != null) return
        listener = object : Listener {
            @EventHandler(priority = EventPriority.MONITOR)
            fun onPlayerJoin(event: PlayerJoinEvent) {
                hideBetween(event.player)
            }

            @EventHandler(priority = EventPriority.MONITOR)
            fun onPlayerQuit(event: PlayerQuitEvent) {
                if (event.player.uniqueId == player.uniqueId) {
                    stop()
                    return
                }
                release(event.player.uniqueId)
            }
        }
        plugin.server.pluginManager.registerEvents(listener!!, plugin)
        server.onlinePlayers.forEach(::hideBetween)
    }

    fun stop() {
        listener?.let { HandlerList.unregisterAll(it) }
        listener = null

        hiddenOwnerFrom.toList().forEach { releasePlayerHide(it, player.uniqueId) }
        hiddenFromOwner.toList().forEach { releasePlayerHide(player.uniqueId, it) }
        hiddenOwnerFrom.clear()
        hiddenFromOwner.clear()
    }

    private fun hideBetween(other: Player) {
        if (!player.isOnline || !other.isOnline || other.uniqueId == player.uniqueId) return
        if (hiddenOwnerFrom.add(other.uniqueId)) {
            retainPlayerHide(other, player)
        }
        if (hiddenFromOwner.add(other.uniqueId)) {
            retainPlayerHide(player, other)
        }
    }

    private fun release(otherId: UUID) {
        if (hiddenOwnerFrom.remove(otherId)) {
            releasePlayerHide(otherId, player.uniqueId)
        }
        if (hiddenFromOwner.remove(otherId)) {
            releasePlayerHide(player.uniqueId, otherId)
        }
    }
}

private val hiddenPlayerPairs = ConcurrentHashMap<PlayerVisibilityPair, Int>()

private fun retainPlayerHide(viewer: Player, target: Player) {
    val key = PlayerVisibilityPair(viewer.uniqueId, target.uniqueId)
    val count = hiddenPlayerPairs.merge(key, 1, Int::plus) ?: 1
    if (count == 1) {
        viewer.hidePlayer(plugin, target)
    }
}

private fun releasePlayerHide(viewerId: UUID, targetId: UUID) {
    val key = PlayerVisibilityPair(viewerId, targetId)
    var shouldShow = false
    hiddenPlayerPairs.compute(key) { _, count ->
        if (count == null) {
            null
        } else if (count <= 1) {
            shouldShow = true
            null
        } else {
            count - 1
        }
    }

    if (!shouldShow) return
    val viewer = Bukkit.getPlayer(viewerId) ?: return
    val target = Bukkit.getPlayer(targetId) ?: return
    if (viewer.isOnline && target.isOnline) {
        viewer.showPlayer(plugin, target)
    }
}

private data class PlayerVisibilityPair(
    val viewerId: UUID,
    val targetId: UUID,
)
