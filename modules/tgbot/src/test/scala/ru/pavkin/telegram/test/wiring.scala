package ru.pavkin.telegram.test

import cats.Applicative
import cats.data.ReaderT
import cats.effect.IO
import cats.mtl.ApplicativeAsk

import com.github.leammas.testkit.statet.ReaderTransform._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import ru.pavkin.telegram.test.state.{StateTodoListStorage, _}
import ru.pavkin.telegram.todolist._

object wiring {

  implicit val aa: ApplicativeAsk[ProcessSyncState, ProcessState] = new ApplicativeAsk[ProcessSyncState, ProcessState] {
    val applicative: Applicative[ProcessSyncState] =
      implicitly[Applicative[ProcessSyncState]]

    def ask: ProcessSyncState[ProcessState] =
      ReaderT[IO, ProcessState, ProcessState] { x =>
        IO.pure(x)
      }

    def reader[A](f: ProcessState => A): ProcessSyncState[A] =
      ReaderT[IO, ProcessState, A] { x =>
        IO.pure(f(x))
      }
  }

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
