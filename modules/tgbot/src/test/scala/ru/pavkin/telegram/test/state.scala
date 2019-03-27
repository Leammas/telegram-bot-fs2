package ru.pavkin.telegram.test

import cats.Monad
import ru.pavkin.telegram.api.{ChatId, Offset, StreamingBotAPI}
import ru.pavkin.telegram.todolist.PostgresTodoListStorage.Record
import ru.pavkin.telegram.todolist.{AdminNotifier, Item, TodoListStorage}
import cats.mtl.MonadState
import monocle.macros.GenLens
import ru.pavkin.telegram.api.dto.BotUpdate
import cats.effect.Sync
import ru.pavkin.telegram.test.statet.HasLens
import ru.pavkin.telegram.test.statet.RefState.RefState
import ru.pavkin.telegram.test.statet.SyncIOState.SyncIOState
import statet.HasLens._

object state {

  final case class ProcessState(records: StateTodoListStorage.InnerState,
                                chatMessages: StateBotApi.InnerState,
                                notifications: StateAdminNotifier.InnerState)

  object ProcessState {
    def empty =
      ProcessState(StateTodoListStorage.InnerState(List.empty),
                   StateBotApi.InnerState(List.empty, List.empty),
                   StateAdminNotifier.InnerState(List.empty))
  }

  type ProcessSyncState[T] = RefState[ProcessState, T]

  object StateTodoListStorage {
    final case class InnerState(value: List[Record]) extends AnyVal

    implicit val lens: HasLens[ProcessState, InnerState] =
      GenLens[ProcessState](_.records).toHasLens

    def apply[F[_]: Monad](
        implicit FMS: MonadState[F, InnerState]
    ): TodoListStorage[F] = new TodoListStorage[F] {
      def addItem(chatId: ChatId, item: Item): F[Unit] =
        FMS.modify(s => s.copy(Record(chatId, item) :: s.value))

      def getItems(chatId: ChatId): F[List[Item]] =
        FMS.inspect(_.value.filter(_.chatId == chatId).map(_.item))

      def clearList(chatId: ChatId): F[Unit] =
        FMS.modify(s => s.copy(s.value.filterNot(_.chatId == chatId)))
    }
  }

  object StateBotApi {
    final case class InnerState(incoming: List[BotUpdate],
                                outgoing: List[(ChatId, String)])

    implicit val lens: HasLens[ProcessState, InnerState] =
      GenLens[ProcessState](_.chatMessages).toHasLens

    def apply[F[_]: Sync](
        implicit FMS: MonadState[F, InnerState]
    ): StreamingBotAPI[F] = new StreamingBotAPI[F] {
      def sendMessage(chatId: ChatId, message: String): F[Unit] =
        FMS.modify(s => s.copy(outgoing = (chatId, message) :: s.outgoing))

      def pollUpdates(fromOffset: Offset): fs2.Stream[F, BotUpdate] =
        fs2.Stream
          .eval(FMS.inspect(_.incoming))
          .flatMap(l => fs2.Stream.fromIterator[F, BotUpdate](l.iterator))
    }
  }

  object StateAdminNotifier {
    final case class InnerState(value: List[ChatId]) extends AnyVal

    implicit val lens: HasLens[ProcessState, InnerState] =
      GenLens[ProcessState](_.notifications).toHasLens

    def apply[F[_]: Monad](
        implicit FMS: MonadState[F, InnerState]): AdminNotifier[F] =
      new AdminNotifier[F] {
        def notify(chatId: ChatId): F[Unit] =
          FMS.modify(s => s.copy(chatId :: s.value))
      }
  }


  //@todo move run here

}
