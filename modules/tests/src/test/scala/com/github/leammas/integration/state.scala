package com.github.leammas.integration

import cats.Monad
import cats.effect.Concurrent
import cats.mtl.MonadState
import com.github.leammas.issue.common.ChatId
import com.github.leammas.issue.issuetracker.Notifications
import com.github.leammas.testkit.statet.HasLens
import com.github.leammas.testkit.statet.RefState.RefState
import fs2.concurrent.Queue
import monocle.macros.GenLens
import ru.pavkin.telegram.todolist.AdminNotifier

object state {

/*  final case class IntegrationState(bot: ru.pavkin.telegram.test.state.ProcessState, issue: Unit)

  type IntegrationSyncState[T] = RefState[???, T]

  object StateAdminNotifier {
    final case class InnerState[F](q: Queue[F, ChatId]) extends AnyVal

    implicit val lens: HasLens[IntegrationState, InnerState] =
      GenLens[ProcessState](_.notifications).toHasLens

    def apply[F[_]](
                            implicit FMS: MonadState[F, InnerState], F: Concurrent[F]): AdminNotifier[F] =
      new AdminNotifier[F] with Notifications[F] {

        private val q = Queue.unbounded[F, ChatId]

        def notify(chatId: ChatId): F[Unit] =
          FMS.modify(s => s.q.enqueue1(chatId))

        def events: fs2.Stream[F, ChatId] = fs2.Stream.eval(F.delay(q.))
      }
  }

  val botApp = ru.pavkin.telegram.test.wiring


  bot.notifier*/

}
