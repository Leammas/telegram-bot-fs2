package ru.pavkin.telegram.test


import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import ru.pavkin.telegram.test.state.{StateTodoListStorage, _}
import ru.pavkin.telegram.todolist._
import cats.mtl.implicits._
import cats.syntax.option._

object wiring {

  val storage = StateTodoListStorage[AsyncTestReader]

  val botApi = StateBotApi[AsyncTestReader]

  val notifier = StateAdminNotifier[AsyncTestReader]

  val phraseChecker = new NaivePhraseChecker[AsyncTestReader]

  val bot = Slf4jLogger.create[AsyncTestReader].map {
    new TodoListBot(botApi, storage, phraseChecker, notifier, _)
  }

  def runTestApp(state: ProcessState): ProcessState = {
    bot
      .flatMap(_.launch.compile.drain)
      .run(state)
      .redeem(_ => state, _ => state)
      .unsafeRunSync()
  }

  def runTestAppWithExplicitError(state: ProcessState): (ProcessState, Option[Throwable]) = {
    bot
      .flatMap(_.launch.compile.drain)
      .run(state)
      .redeem(err => (state, err.some), _ => (state, None))
      .unsafeRunSync()
  }

}
