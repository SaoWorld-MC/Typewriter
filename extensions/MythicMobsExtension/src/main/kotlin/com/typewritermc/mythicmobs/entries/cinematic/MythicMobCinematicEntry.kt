package com.typewritermc.mythicmobs.entries.cinematic

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Placeholder
import com.typewritermc.core.extension.annotations.Segments
import com.typewritermc.core.extension.annotations.WithRotation
import com.typewritermc.core.utils.point.Position
import com.typewritermc.core.utils.switchContext
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.entries.*
import com.typewritermc.engine.paper.entry.temporal.SimpleCinematicAction
import com.typewritermc.engine.paper.extensions.placeholderapi.parsePlaceholders
import com.typewritermc.engine.paper.plugin
import com.typewritermc.engine.paper.utils.Sync
import com.typewritermc.engine.paper.utils.server
import com.typewritermc.engine.paper.utils.toBukkitLocation
import io.lumine.mythic.bukkit.MythicBukkit
import io.lumine.mythic.core.mobs.ActiveMob
import kotlinx.coroutines.Dispatchers
import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.util.UUID

@Entry("mythicmob_cinematic", "Spawn a MythicMob during a cinematic", Colors.PURPLE, "fa6-solid:dragon")
/**
 * The `Spawn MythicMob Cinematic` cinematic entry spawns a MythicMob during a cinematic.
 *
 * ## How could this be used?
 *
 * This can be used to animate a MythicMob spawning during a cinematic.
 */
class MythicMobCinematicEntry(
    override val id: String = "",
    override val name: String = "",
    override val criteria: List<Criteria> = emptyList(),
    @Segments(Colors.PURPLE, "fa6-solid:dragon")
    val segments: List<MythicMobSegment> = emptyList(),
) : CinematicEntry {
    override fun create(player: Player): CinematicAction {
        return MobCinematicAction(player, this)
    }
}

data class MythicMobSegment(
    override val startFrame: Int = 0,
    override val endFrame: Int = 0,
    @Placeholder
    val mobName: Var<String> = ConstVar(""),
    @WithRotation
    val location: Var<Position> = ConstVar(Position.ORIGIN),
) : Segment

class MobCinematicAction(
    private val player: Player,
    entry: MythicMobCinematicEntry,
) : SimpleCinematicAction<MythicMobSegment>() {
    override val segments: List<MythicMobSegment> = entry.segments

    private var mob: ActiveMob? = null
    private val trackedEntities = mutableSetOf<UUID>()
    private var listener: Listener? = null

    override suspend fun startSegment(segment: MythicMobSegment) {
        super.startSegment(segment)

        Dispatchers.Sync.switchContext {
            unregisterListener()

            val mob =
                MythicBukkit.inst().mobManager.spawnMob(
                    segment.mobName.get(player).parsePlaceholders(player),
                    segment.location.get(player).toBukkitLocation()
                )
            this@MobCinematicAction.mob = mob

            val entity = mob.entity.bukkitEntity ?: return@switchContext
            track(entity)
            listener = object : Listener {
                @EventHandler(priority = EventPriority.MONITOR)
                fun onPlayerJoin(event: PlayerJoinEvent) {
                    hideTrackedEntities(event.player)
                }
            }
            plugin.server.pluginManager.registerEvents(listener!!, plugin)
            hideTrackedEntities()
        }
    }

    override suspend fun tickSegment(segment: MythicMobSegment, frame: Int) {
        super.tickSegment(segment, frame)
        if (frame % 2 != 0) return
        Dispatchers.Sync.switchContext {
            hideTrackedEntities()
        }
    }

    override suspend fun stopSegment(segment: MythicMobSegment) {
        super.stopSegment(segment)

        Dispatchers.Sync.switchContext {
            try {
                mob?.despawn()
            } finally {
                unregisterListener()
                mob = null
                trackedEntities.clear()
            }
        }
    }

    private fun hideTrackedEntities() {
        currentEntities().forEach { entity ->
            server.onlinePlayers
                .filter { it.uniqueId != player.uniqueId }
                .forEach { it.hideEntity(plugin, entity) }
        }
    }

    private fun hideTrackedEntities(viewer: Player) {
        if (viewer.uniqueId == player.uniqueId) return
        currentEntities().forEach { viewer.hideEntity(plugin, it) }
    }

    private fun currentEntities(): List<Entity> {
        mob?.entity?.bukkitEntity?.let(::track)
        return trackedEntities.mapNotNull { Bukkit.getEntity(it) }
            .filter { it.isValid && !it.isDead }
    }

    private fun track(entity: Entity) {
        if (!entity.isValid || entity.isDead) return
        trackedEntities.add(entity.uniqueId)
        entity.passengers.forEach(::track)
    }

    private fun unregisterListener() {
        listener?.let {
            HandlerList.unregisterAll(it)
            listener = null
        }
    }
}
