package ru.pavkin.telegram.test

//import cats.mtl.instances.state._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import ru.pavkin.telegram.test.statet.MonadStateTransform._
import ru.pavkin.telegram.test.state._
import ru.pavkin.telegram.todolist._
import ru.pavkin.telegram.test.statet.RefState._

object wiring {

  val storage = StateTodoListStorage[ProcessSyncState]

  val botApi = StateBotApi[ProcessSyncState]

  val notifier = StateAdminNotifier[ProcessSyncState]

  val phraseChecker = new NaivePhraseChecker[ProcessSyncState]

  val bot = Slf4jLogger.create[ProcessSyncState].map {
    new TodoListBot(botApi, storage, phraseChecker, notifier, _)
  }

}
