package ProxyBwapi.Players

import Lifecycle.With
import bwapi.Player

object Players {
  def all: Iterable[PlayerInfo] = With.proxy.players

  def get(player: Player): PlayerInfo = {
    if (player == null) {
      throw new IllegalArgumentException(
        "Players.get(null): bwapi Player missing (game.self/neutral null — often isReplay=true or Snapshot self unset)")
    }
    if (With.proxy == null) {
      throw new IllegalStateException("Players.get: With.proxy not initialized")
    }
    val id = player.getID
    val byId = With.proxy.playersById
    if (id < 0 || id >= byId.length) {
      throw new IllegalStateException(s"Players.get: id=$id out of range [0,${byId.length})")
    }
    val info = byId(id)
    if (info == null) {
      throw new IllegalStateException(s"Players.get: no PlayerInfo for id=$id (playersById hole)")
    }
    info
  }
}
