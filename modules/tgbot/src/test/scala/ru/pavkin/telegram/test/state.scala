package ru.pavkin.telegram.test

import cats.Monad
import cats.data.ReaderT
import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import cats.mtl.ApplicativeAsk
import com.olegpy.meow.optics.MkLensToType
import fs2.concurrent.InspectableQueue
import ru.pavkin.telegram.api.dto.BotUpdate
import ru.pavkin.telegram.api.{ChatId, Offset, StreamingBotAPI}
import ru.pavkin.telegram.todolist.PostgresTodoListStorage.Record
import ru.pavkin.telegram.todolist.{AdminNotifier, Item, TodoListStorage}
import shapeless.=:!=

object state {

  final case class ProcessState(records: StateTodoListStorage.InnerState,
                                chatMessages: StateBotApi.InnerState,
                                notifications: StateAdminNotifier.InnerState) {
    def isEmpty =
      records.value.get
        .unsafeRunSync()
        .isEmpty && chatMessages.incoming.get
        .unsafeRunSync()
        .isEmpty && chatMessages.outgoing.get
        .unsafeRunSync()
        .isEmpty && notifications.queue.getSize.unsafeRunSync() === 0
  }

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
        an <- InspectableQueue
          .unbounded[IO, ChatId]
          .map(StateAdminNotifier.InnerState)
      } yield
        ProcessState(tds, StateBotApi.InnerState(bai, bao), an)).unsafeRunSync()
    }
  }

  type AsyncTestReader[T] = ReaderT[IO, ProcessState, T]

  type AbstractTestReader[F[_]] = ApplicativeAsk[F, ProcessState]

  implicit def asyncReaderDeriver[F[_]: AbstractTestReader, A](
      implicit neq: ProcessState =:!= A,
      mkLensToType: MkLensToType[ProcessState, A]): ApplicativeAsk[F, A] =
    com.olegpy.meow.hierarchy.deriveApplicativeAsk[F, ProcessState, A]

  object StateTodoListStorage {
    final case class InnerState(value: Ref[IO, List[Record]]) extends AnyVal

    def apply[F[_]: Monad: LiftIO](
        implicit AA: ApplicativeAsk[F, InnerState]
    ): TodoListStorage[F] = new TodoListStorage[F] {
      def addItem(chatId: ChatId, item: Item): F[Unit] =
        AA.ask.flatMap(_.value.update(s => Record(chatId, item) :: s).to[F])

      def getItems(chatId: ChatId): F[List[Item]] =
        AA.ask.flatMap(
          _.value.get.to[F].map(_.filter(_.chatId == chatId).map(_.item)))

      def clearList(chatId: ChatId): F[Unit] =
        AA.ask.flatMap(
          _.value.update(s => s.filterNot(_.chatId == chatId)).to[F])
    }
  }

  object StateBotApi {
    final case class InnerState(incoming: Ref[IO, List[BotUpdate]],
                                outgoing: Ref[IO, List[(ChatId, String)]])

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
    final case class InnerState(queue: InspectableQueue[IO, ChatId])
        extends AnyVal

    def apply[F[_]: Monad: LiftIO](
        implicit AA: ApplicativeAsk[F, InnerState]): AdminNotifier[F] =
      new AdminNotifier[F] {
        def notify(chatId: ChatId): F[Unit] = {
          AA.ask.flatMap(_.queue.enqueue1(chatId).to[F])
        }

      }
  }

}
