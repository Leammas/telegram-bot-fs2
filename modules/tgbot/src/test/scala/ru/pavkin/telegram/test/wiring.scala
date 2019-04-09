package ru.pavkin.telegram.test

import com.github.leammas.testkit.statet.ReaderTransform._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import ru.pavkin.telegram.test.state.{StateTodoListStorage, _}
import ru.pavkin.telegram.todolist._
import cats.mtl.implicits._

object wiring {

  val storage = StateTodoListStorage[ProcessSyncState]

  val botApi = StateBotApi[ProcessSyncState]

  val notifier = StateAdminNotifier[ProcessSyncState]

  val phraseChecker = new NaivePhraseChecker[ProcessSyncState]

  val bot = Slf4jLogger.create[ProcessSyncState].map {
    new TodoListBot(botApi, storage, phraseChecker, notifier, _)
  }

  def runTestApp(state: ProcessState): ProcessState = {
    bot
      .flatMap(_.launch.compile.drain)
      .run(state)
      .redeem(_ => state, _ => state)
      .unsafeRunSync()
  }

}
