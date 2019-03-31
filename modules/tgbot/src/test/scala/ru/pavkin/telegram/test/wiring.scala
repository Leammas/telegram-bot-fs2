package ru.pavkin.telegram.test

import cats.effect.{ContextShift, IO}
import cats.mtl.instances.readert._
import cats.mtl.instances.ask._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import com.github.leammas.testkit.statet.MonadStateTransform._
import ru.pavkin.telegram.test.state._
import ru.pavkin.telegram.todolist._

object wiring {

  implicit val shift: ContextShift[IO] =
    cats.effect.internals.IOContextShift.global

  val storage = StateTodoListStorage[ProcessSyncState]

  val botApi = StateBotApi[ProcessSyncState]

  val notifier = StateAdminNotifier[ProcessSyncState]

  val phraseChecker = new NaivePhraseChecker[ProcessSyncState]

  val bot = Slf4jLogger.create[ProcessSyncState].map {
    new TodoListBot(botApi, storage, phraseChecker, notifier, _)
  }

  /*private def run(state: ProcessState) = bot
  .flatMap(_.launch.compile.drain)
  .runS(state)
  .unsafeRunSync()*/

  def runTestApp(
      state: ProcessState[IO]): ProcessState[IO] =
    bot
      .flatMap(_.launch.compile.drain)
      .run(state)
      .redeem(_ => state, _ => state)
      .unsafeRunSync()

}
