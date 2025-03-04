package com.kruthers.gamemode4core.events

import com.kruthers.gamemode4core.Gamemode4Core
import com.kruthers.gamemode4core.modes.ModMode
import com.kruthers.gamemode4core.modes.StreamerMode
import com.kruthers.gamemode4core.modes.Watching
import com.kruthers.gamemode4core.utils.getMessage
import com.kruthers.gamemode4core.utils.loadPlayerData
import com.kruthers.gamemode4core.utils.parseString
import com.kruthers.gamemode4core.utils.playerHasData
import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.World
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Score
import java.lang.Exception
import java.util.*

class PlayerConnectionEvents(val plugin: Gamemode4Core): Listener {
    private val tagPrefix: String = "\u1747gm4Core_"

    //regex
    private val usernameRegex: Regex = Regex("${tagPrefix}username_([\\w_]{3,16})")


    companion object {
        fun getConfigSpawn(plugin: Gamemode4Core): Location {
            val config: FileConfiguration = plugin.config

            val x: Double = config.getDouble("spawn.x")
            val y: Double = config.getDouble("spawn.y")
            val z: Double = config.getDouble("spawn.z")

            val pitch: Float = config.getDouble("spawn.pitch").toFloat()
            val yaw: Float = config.getDouble("spawn.yaw").toFloat()

            val worldName: String = config.getString("spawn.world").toString()
            var world: World? = Bukkit.getWorld(worldName)
            if (world == null) {
                world = Bukkit.getWorlds()[0]
            }

            return Location(world,x,y,z,yaw,pitch)
        }
    }


    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player: Player = event.player;

        //Gives them a tag containing there current username to monitor for changes
        if (!player.hasPlayedBefore()) {
            // player has not played before so just gives them the tag it will also handel first join events
            val location: Location = getConfigSpawn(plugin);
            player.setBedSpawnLocation(location,true)
            player.teleport(location)

            player.addScoreboardTag("${tagPrefix}username_${player.name}")

        } else {
            //player has played before to searches there tags to try and find the saved username
            var username: String = ""
            player.scoreboardTags.forEach {
                val found = usernameRegex.find(it)
                if (found!=null) {
                    username = found.groups[1]?.value.toString()
                }
            }

            // checks if username is saved
            if (username == "") {
                // -> username is not saved, so will just add the tag
                player.addScoreboardTag("${tagPrefix}username_${player.name}")
            } else {
                // -> username is found, will check if it matches there current username
                if (username != player.name) {
                    // -> if it does not match it will remove it and add a new tag with the update name
                    player.addScoreboardTag("${tagPrefix}username_${player.name}")
                    player.removeScoreboardTag("${tagPrefix}username_${username}")

                    //TODO check if works
                    //Now will parse all the scoreboards that are tracked and copy the scores over
                    val scoreboards: MutableList<String> = plugin.config.getStringList("scoreboard.tracked")
                    scoreboards.forEach {
                        val scoreboard: Objective? = Bukkit.getServer().scoreboardManager?.mainScoreboard?.getObjective(it)
                        val score: Score? = scoreboard?.getScore(username)
                        score?.score.also { scoreboard?.getScore(player.name)?.score = it as Int }
                    }
                }
            }
        }


        // This handels players using watch, build and stream mode.
        //first checks if they have any data
        if (playerHasData(plugin, player)) {
            //if they have saved data they will load it and check for diffrent modes
            val playerData: YamlConfiguration = loadPlayerData(plugin, player)

            //if stream mode is enabled it will send a reminder as long as they still have perms
            if (playerData.getBoolean("mode.streamer")) {
                if (player.hasPermission("gm4core.mode.streamer")) {
                    player.sendMessage(getMessage(plugin, "streammode.join"))
                } else {
                    StreamerMode.toggle(plugin,player)
                }
            }

            //mod mode
            if (playerData.getBoolean("mode.mod_mode")) {
                if (player.hasPermission("gm4core.mode.mod")) {
                    player.sendMessage(getMessage(plugin, "mod_mode.join"))

                    if (!Gamemode4Core.modModeBossBar.players.contains(player)) {
                        try {
                            Gamemode4Core.modModeBossBar.players.add(player)
                        } catch (e: Exception) {
                            player.sendMessage(parseString("{prefix} ${ChatColor.RED}Failed to add player to boss bar",plugin))
                        }
                    }

                } else {
                    ModMode.disable(plugin,player,playerData)
                }
            }

            //watch mode
            if (playerData.getBoolean("mode.watching")) {
                if (player.hasPermission("gm4core.mode.watch")) {
                    val target: OfflinePlayer? = playerData.getString("storage.watching.target")?.let { Bukkit.getOfflinePlayer(UUID.fromString(it)) }

                    if (target == null) {
                        Watching.disable(plugin, player, playerData)
                    } else {
                        player.sendMessage(getMessage(plugin, "watch.join").replace("{target}",target.name?:"null"))
                        Gamemode4Core.watchingPlayers[player] = target.uniqueId
                    }
                } else {
                    ModMode.disable(plugin,player,playerData)
                }
            }
        }

        // If the server is in freeze mode it will send a message informing them
        if (Gamemode4Core.playersFrozen) {
            event.player.sendMessage("${ChatColor.AQUA}Everyone is currently frozen while the mods resolve an issue, please stand still")
            event.player.sendTitle("${ChatColor.AQUA}Everyone is currently frozen while the mods resolve an issue, please stand still","",0,200,20)
        }

    }


    @EventHandler
    fun onPlayerLeave(event: PlayerQuitEvent) {
        val player: Player = event.player

        //checks if the player is being watched
        if (Gamemode4Core.watchingPlayers.values.contains(player.uniqueId)) {
            // if they are it will send a message to the watcher informing them
            Gamemode4Core.watchingPlayers.forEach { watcher, target ->
                if (target == player.uniqueId) {
                    watcher.sendMessage(getMessage(plugin,"watch.logout",watcher).replace("{target}",player.name))
                }
            }
        }

        // if they are watching someone it will remove them from the monitoring list
        if (Gamemode4Core.watchingPlayers.keys.contains(player)) {
            Gamemode4Core.watchingPlayers.remove(player)
        }
    }

}