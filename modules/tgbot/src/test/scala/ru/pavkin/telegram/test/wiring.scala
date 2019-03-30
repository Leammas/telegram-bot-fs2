package ru.pavkin.telegram.test

//import cats.mtl.instances.state._
import cats.effect.SyncIO
import cats.effect.concurrent.Ref
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import com.github.leammas.testkit.statet.MonadStateTransform._
import ru.pavkin.telegram.test.state._
import ru.pavkin.telegram.todolist._
import com.github.leammas.testkit.statet.RefState._

object wiring {

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

  def runTestApp(state: ProcessState): ProcessState = Ref.of[SyncIO, ProcessState](state).flatMap { refState =>
    bot
      .flatMap(_.launch.compile.drain)
      .runS(refState).handleErrorWith(_ => SyncIO.pure(refState)).flatMap(_.get)
  }.unsafeRunSync()

}
