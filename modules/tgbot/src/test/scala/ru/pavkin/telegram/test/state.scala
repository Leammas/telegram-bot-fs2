package ru.pavkin.telegram.test

import cats.Monad
import cats.data.ReaderT
import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import cats.mtl.ApplicativeAsk
import com.github.leammas.testkit.statet.HasLens
import com.github.leammas.testkit.statet.HasLens._
import fs2.concurrent.Queue
import monocle.macros.GenLens
import ru.pavkin.telegram.api.dto.BotUpdate
import ru.pavkin.telegram.api.{ChatId, Offset, StreamingBotAPI}
import ru.pavkin.telegram.todolist.PostgresTodoListStorage.Record
import ru.pavkin.telegram.todolist.{AdminNotifier, Item, TodoListStorage}

object state {

  //@todo autolens
  final case class ProcessState[F[_]](
      records: StateTodoListStorage.InnerState[F],
      chatMessages: StateBotApi.InnerState[F],
      notifications: StateAdminNotifier.InnerState[F])

  object ProcessState {
    def empty[F[_]: Concurrent]: F[ProcessState[F]] =
      for {
        tds <- Ref
          .of[F, List[Record]](List.empty)
          .map(StateTodoListStorage.InnerState(_))
        bao <- Ref.of[F, List[(ChatId, String)]](List.empty)
        bai <- Ref.of[F, List[BotUpdate]](List.empty)
        an <- Queue.unbounded[F, ChatId].map(StateAdminNotifier.InnerState(_))
      } yield ProcessState(tds, StateBotApi.InnerState(bai, bao), an)
  }

  type ProcessSyncState[T] = ReaderT[IO, ProcessState[IO], T]

  object StateTodoListStorage {
    final case class InnerState[F[_]](value: Ref[F, List[Record]])
        extends AnyVal

    implicit def lens[F[_]]: HasLens[ProcessState[F], InnerState[F]] =
      GenLens[ProcessState[F]](_.records).toHasLens

    def apply[F[_]: Monad](
        implicit AA: ApplicativeAsk[F, InnerState[F]]
    ): TodoListStorage[F] = new TodoListStorage[F] {
      def addItem(chatId: ChatId, item: Item): F[Unit] =
        AA.ask.flatMap(_.value.update(s => Record(chatId, item) :: s.value))

      def getItems(chatId: ChatId): F[List[Item]] =
        AA.ask.flatMap(
          _.value.get.map(_.filter(_.chatId == chatId).map(_.item)))

      def clearList(chatId: ChatId): F[Unit] =
        AA.ask.flatMap(_.value.update(s =>
          s.value.filterNot(_.chatId == chatId)))
    }
  }

  object StateBotApi {
    final case class InnerState[F[_]](incoming: Ref[F, List[BotUpdate]],
                                      outgoing: Ref[F, List[(ChatId, String)]])

    implicit def lens[F[_]]: HasLens[ProcessState[F], InnerState[F]] =
      GenLens[ProcessState[F]](_.chatMessages).toHasLens

    def apply[F[_]: Sync](
        implicit AA: ApplicativeAsk[F, InnerState[F]]
    ): StreamingBotAPI[F] = new StreamingBotAPI[F] {
      def sendMessage(chatId: ChatId, message: String): F[Unit] =
        AA.ask.flatMap(_.outgoing.update(s => (chatId, message) :: s))

      def pollUpdates(fromOffset: Offset): fs2.Stream[F, BotUpdate] =
        fs2.Stream
          .eval(AA.ask.flatMap(_.incoming.get))
          .flatMap(l => fs2.Stream.fromIterator[F, BotUpdate](l.iterator))
    }
  }

  object StateAdminNotifier {
    import cats.implicits._
    final case class InnerState[F[_]](q: Queue[F, ChatId]) extends AnyVal

    implicit def lens[F[_]]: HasLens[ProcessState[F], InnerState[F]] =
      GenLens[ProcessState[F]](_.notifications).toHasLens

    def apply[F[_]: Monad](
        implicit AA: ApplicativeAsk[F, InnerState[F]]): AdminNotifier[F] =
      new AdminNotifier[F] {
        def notify(chatId: ChatId): F[Unit] =
          AA.ask.flatMap(_.q.enqueue1(chatId))
      }
  }

}
