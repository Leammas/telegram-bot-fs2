package ru.pavkin.telegram.test

import cats.Monad
import cats.data.ReaderT
import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import cats.mtl.ApplicativeAsk
import com.github.leammas.testkit.statet.HasLens
import com.github.leammas.testkit.statet.HasLens._
import fs2.concurrent.{InspectableQueue, Queue}
import monocle.macros.GenLens
import ru.pavkin.telegram.api.dto.BotUpdate
import ru.pavkin.telegram.api.{ChatId, Offset, StreamingBotAPI}
import ru.pavkin.telegram.todolist.PostgresTodoListStorage.Record
import ru.pavkin.telegram.todolist.{AdminNotifier, Item, TodoListStorage}

object state {

  //@todo autolens
  final case class ProcessState(records: StateTodoListStorage.InnerState,
                                chatMessages: StateBotApi.InnerState,
                                notifications: StateAdminNotifier.InnerState)

  object ProcessState {
    private implicit val shift: ContextShift[IO] =
      cats.effect.internals.IOContextShift.global

    def init(records: List[Record] = List.empty,
             outgoingMessages: List[(ChatId, String)] = List.empty,
             incomingMessages: List[BotUpdate] = List.empty): ProcessState = {

      (for {
        tds <- Ref
          .of[IO, List[Record]](records)
          .map(StateTodoListStorage.InnerState)
        bao <- Ref.of[IO, List[(ChatId, String)]](outgoingMessages)
        bai <- Ref.of[IO, List[BotUpdate]](incomingMessages)
        an <- InspectableQueue.unbounded[IO, ChatId].map(StateAdminNotifier.InnerState)
      } yield
        ProcessState(tds, StateBotApi.InnerState(bai, bao), an)).unsafeRunSync()
    }
  }

  type ProcessSyncState[T] = ReaderT[IO, ProcessState, T]

  object StateTodoListStorage {
    final case class InnerState(value: Ref[IO, List[Record]]) extends AnyVal

    implicit def lens: HasLens[ProcessState, InnerState] =
      GenLens[ProcessState](_.records).toHasLens

    def apply[F[_]: Monad: LiftIO](
        implicit AA: ApplicativeAsk[F, InnerState]
    ): TodoListStorage[F] = new TodoListStorage[F] {
      def addItem(chatId: ChatId, item: Item): F[Unit] =
        AA.ask.flatMap(
          _.value.update(s => Record(chatId, item) :: s.value).to[F])

      def getItems(chatId: ChatId): F[List[Item]] =
        AA.ask.flatMap(
          _.value.get.to[F].map(_.filter(_.chatId == chatId).map(_.item)))

      def clearList(chatId: ChatId): F[Unit] =
        AA.ask.flatMap(
          _.value.update(s => s.value.filterNot(_.chatId == chatId)).to[F])
    }
  }

  object StateBotApi {
    final case class InnerState(incoming: Ref[IO, List[BotUpdate]],
                                outgoing: Ref[IO, List[(ChatId, String)]])

    implicit def lens: HasLens[ProcessState, InnerState] =
      GenLens[ProcessState](_.chatMessages).toHasLens

    def apply[F[_]: Sync: LiftIO](
        implicit AA: ApplicativeAsk[F, InnerState]
    ): StreamingBotAPI[F] = new StreamingBotAPI[F] {
      def sendMessage(chatId: ChatId, message: String): F[Unit] =
        AA.ask.flatMap(_.outgoing.update(s => (chatId, message) :: s).to[F])

      def pollUpdates(fromOffset: Offset): fs2.Stream[F, BotUpdate] =
        fs2.Stream
          .eval(AA.ask.flatMap(_.incoming.get.to[F]))
          .flatMap(l => fs2.Stream.fromIterator[F, BotUpdate](l.iterator))
    }
  }

  object StateAdminNotifier {
    final case class InnerState(q: InspectableQueue[IO, ChatId]) extends AnyVal

    implicit def lens: HasLens[ProcessState, InnerState] =
      GenLens[ProcessState](_.notifications).toHasLens

    def apply[F[_]: Monad: LiftIO](
        implicit AA: ApplicativeAsk[F, InnerState]): AdminNotifier[F] =
      new AdminNotifier[F] {
        def notify(chatId: ChatId): F[Unit] =
          AA.ask.flatMap(_.q.enqueue1(chatId).to[F])
      }
  }

}
