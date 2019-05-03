package com.github.leammas.issue.issuetracker

import java.util.UUID

import aecor.MonadActionLiftReject
import aecor.encoding.{KeyDecoder, KeyEncoder}
import aecor.journal.postgres.PostgresEventJournal.Serializer
import cats.{Functor, Monad}
import cats.data.Chain
import cats.effect.SyncIO
import com.github.leammas.issue.common.{ChatId, JsonPersistence}
import com.github.leammas.issue.issuetracker.Issue.{Comment, Description}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto
import aecor.data.Folded.syntax._
import aecor.data._
import com.github.leammas.issue.issuetracker.IssueEvent.{IssueCommentAdded, IssueCreated, IssueResolved}
import cats.implicits._

sealed abstract class IssueEvent extends Product with Serializable
object IssueEvent {
  final case class IssueCreated(chatId: Long, description: Description)
    extends IssueEvent
  final case class IssueCommentAdded(message: Comment) extends IssueEvent
  final case object IssueResolved extends IssueEvent

  implicit val jsonEncoder: Encoder[IssueEvent] = semiauto.deriveEncoder
  implicit val jsonDecoder: Decoder[IssueEvent] = semiauto.deriveDecoder

  implicit val persistentSerializer: Serializer[IssueEvent] =
    JsonPersistence.jsonSerializer[IssueEvent]
}

final case class IssueState(chatId: ChatId,
                            isResolved: Boolean,
                            description: Description,
                            comments: List[Comment]) {
  def handleEvent(e: IssueEvent): Folded[IssueState] = e match {
    case _: IssueEvent.IssueCreated => impossible
    case IssueEvent.IssueCommentAdded(message) =>
      copy(comments = message :: comments).next
    case IssueEvent.IssueResolved => copy(isResolved = true).next
  }
}

object IssueState {
  def init(e: IssueEvent): Folded[IssueState] = e match {
    case IssueEvent.IssueCreated(chatId, description) =>
      IssueState(chatId, isResolved = false, description, List.empty).next
    case _: IssueEvent => impossible
  }
}

final class EventSourcedIssue[F[_]: Functor, I[_]](
                                                    implicit I: MonadActionLiftReject[I,
  F,
  Option[IssueState],
  IssueEvent,
  IssueRejection])
  extends Issue[I] {

  import I._

  def create(chatId: ChatId, description: Description): I[Unit] = read.flatMap {
    case Some(_) => reject(IssueRejection.AlreadyExists)
    case None    => append(IssueCreated(chatId, description))
  }

  def markResolved: I[Unit] = read.flatMap {
    case Some(x) if !x.isResolved => append(IssueResolved)
    case Some(_)                  => I.unit
    case None                     => reject(IssueRejection.NotExists)
  }

  def comment(message: Comment): I[Unit] = read.flatMap {
    case Some(_) => append(IssueCommentAdded(message))
    case None    => reject(IssueRejection.NotExists)
  }

  def getState: I[Option[IssueState]] = read
}

object EventSourcedIssue {

  class Applier[F[_]: Functor] {
    def foo[I[_]](
                   implicit I: MonadActionLiftReject[I,
      F,
      Option[IssueState],
      IssueEvent,
      IssueRejection]): Issue[I] = new EventSourcedIssue[F, I]
  }

  def apply[F[_]: Functor] = new Applier[F]

  def behavior[F[_]: Monad]
  : EventsourcedBehavior[EitherK[Issue, IssueRejection, ?[_]],
    F,
    Option[
      IssueState
      ],
    IssueEvent] =
    EventsourcedBehavior.optionalRejectable(apply[F].foo,
      IssueState.init,
      _.handleEvent(_))

  val entityName: String = "Issue"
  val entityNameTag: EventTag = EventTag(entityName)
  val tagging: Tagging[IssueId] = Tagging.partitioned(20)(entityNameTag)

  implicit val paymentKeyEncoder: KeyEncoder[IssueId] =
    KeyEncoder.instance[IssueId](_.toString)

  implicit val paymentKeyDecoder: KeyDecoder[IssueId] =
    KeyDecoder.instance[IssueId](
      x =>
        SyncIO(UUID.fromString(x)).attempt
          .map(_.toOption.map(IssueId))
          .unsafeRunSync())
}

