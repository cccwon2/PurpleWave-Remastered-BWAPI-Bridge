# Three-boundary pylon diagnostics (A1 vs A2)

Apply on PurpleWave (matchstart patches already on). Rebuild jar. Stay on Pluto **rc3**. No force-true canBuild / no Build inject.

## 1) `Micro/Actions/Basic/Build.scala`

After `var movePixel = tile.topLeftPixel.add(building.tileWidth * 16, building.tileHeight * 16)` and **before** the `intent.startNow && …` block:

```scala
    if (With.frame % 24 == unit.id % 24) {
      val dist = unit.pixelDistanceCenter(movePixel)
      val why =
        if (!intent.startNow) "startNow=false"
        else if (!tile.visible) "tile.notVisible"
        else if (dist >= 112) f"dist=$dist>=112"
        else if (With.self.minerals < building.mineralPrice - 8) "minerals"
        else if (With.self.gas < building.gasPrice - 8) "gas"
        else "gates_ok"
      With.logger.debug(
        f"build-gate u=${unit.id} type=${building} tile=$tile startNow=${intent.startNow} " +
        f"vis=${tile.visible} explored=${tile.explored} dist=$dist " +
        f"mins=${With.self.minerals}/${building.mineralPrice} gas=${With.self.gas}/${building.gasPrice} why=$why")
    }
```

Immediately **before** `Commander.build(...)` inside that if:

```scala
      With.logger.debug(f"build-precall u=${unit.id} type=$building tile=$tile")
```

## 2) `Micro/Agency/Commander.scala` — `build(unit, unitClass, tile)`

Replace the non-addon issue path with logging (still call real APIs; do not override results):

```scala
    } else {
      val tp = tile.bwapi
      val ut = unitClass.bwapiType
      val can = unit.bwapiUnit.canBuild(ut, tp)
      val explored = With.game.isExplored(tp)
      val buildable = With.game.isBuildable(tp)
      val canHere = With.game.canBuildHere(tp, ut, unit.bwapiUnit)
      val blockers = tile.units.filter(u => u != unit && !u.flying && u.visible).map(u => s"${u.id}:${u.unitClass}").mkString(",")
      With.logger.debug(
        f"build-call u=${unit.id} type=$unitClass tile=$tile canBuild=$can canHere=$canHere " +
        f"explored=$explored buildable=$buildable blockers=[$blockers] (pre-issue)")
      val issued = unit.bwapiUnit.build(ut, tp)
      With.logger.debug(f"build-call u=${unit.id} issued=$issued lastCmd=${unit.bwapiUnit.getLastCommand.getUnitCommandType}")
    }
```

## 3) Bridge (observe — already on rc3)

Unchanged. Confirm:

- `kind":"build"` only if Build entered `unitCommands`
- `0c` only after encode/submit
- Full `bridge.log` through disconnect (not a mid-match clip)

## Greps

```
build-gate
build-precall
build-call
kind":"build"
packet_hex":"0c
jbwapi_server_started
```
