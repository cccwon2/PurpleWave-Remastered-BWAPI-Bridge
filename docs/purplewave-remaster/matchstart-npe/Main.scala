package Lifecycle

import Mathematics.Maff
import Utilities.?
import bwapi.BWClientConfiguration

import java.lang.management.ManagementFactory
import scala.collection.JavaConverters._

object Main {

  private val JBWAPIClientDataSizeBytes =  33017048
  private val botRequiredDataSizeBytes  = 650 * 1000 * 1000
  private val memoryFree  : Long            = Math.min(Runtime.getRuntime.maxMemory, Runtime.getRuntime.totalMemory)
  val jvmRuntimeArguments : Vector[String]  = ManagementFactory.getRuntimeMXBean.getInputArguments.asScala.toVector
  val liveDebugging       : Boolean         = jvmRuntimeArguments.exists(_.contains("purpledebug"))
  // Remaster/JBWAPI server: prefer sync (matches ConnectionProbe). Async unsafe
  // can tear GameData bools (isReplay) and leave Game.self null at MatchStart.
  val forceSync           : Boolean         =
    jvmRuntimeArguments.exists(_.contains("purplewave.jbwapi.sync=true")) ||
      Option(System.getProperty("purplewave.jbwapi.sync")).exists(v => v == "true" || v == "1")
  val framesBufferable    : Int             = ((memoryFree - botRequiredDataSizeBytes) / JBWAPIClientDataSizeBytes).toInt
  val useFrameBuffer      : Boolean         = ! liveDebugging && ! forceSync
  val framesToBuffer      : Int             = ?(useFrameBuffer, Maff.clamp(framesBufferable, 1, 10), 0)

  val jbwapiConfiguration: BWClientConfiguration = new BWClientConfiguration()
    .withAutoContinue(false)
    .withMaxFrameDurationMs(30)
    .withAsyncFrameBufferCapacity(framesToBuffer)
    .withAsync(useFrameBuffer)
    .withAsyncUnsafe(useFrameBuffer)
    .withDebugConnection(true)

  def main(args: Array[String]): Unit = {
    System.out.println(f"PurpleWave Main.main() invoked ${System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean.getStartTime}ms after VM start.")
    System.out.println(f"JBWAPI async=${useFrameBuffer} forceSync=$forceSync framesToBuffer=$framesToBuffer")
    PurpleBWClient.startGame(jbwapiConfiguration)
  }
}
