package com.github.leammas.issue.issuetracker

import aecor.macros.boopickleWireProtocol
import cats.tagless.autoFunctorK
import boopickle.Default._
import Issue._
import aecor.MonadActionLiftReject
import aecor.runtime.akkapersistence.serialization._
import com.github.leammas.issue.common.ChatId
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto
import com.github.leammas.issue.common.JsonPersistence._

import scala.language.higherKinds
import aecor.data.Folded
import aecor.data.Folded.syntax._
import cats.{Applicative, Functor}
import cats.data.Chain
import cats.implicits._
import com.github.leammas.issue.issuetracker.IssueEvent.{
  IssueCommentAdded,
  IssueCreated,
  IssueResolved
}

final case class IssueId(value: java.util.UUID) extends AnyVal

@boopickleWireProtocol
@autoFunctorK(false)
trait Issue[F[_]] {
  def create(chatId: ChatId, description: Description): F[Unit]
  def markResolved: F[Unit]
  def comment(message: Comment): F[Unit]
}

object Issue {
  type Description = String
  type Comment = String
}

sealed trait IssueRejection extends Product with Serializable
object IssueRejection {
  case object AlreadyExists extends IssueRejection
  case object NotExists extends IssueRejection
}

sealed abstract class IssueEvent extends Product with Serializable
object IssueEvent {
  final case class IssueCreated(chatId: Long, description: Description)
      extends IssueEvent
  final case class IssueCommentAdded(message: Comment) extends IssueEvent
  final case object IssueResolved extends IssueEvent

  val jsonEncoder: Encoder[IssueEvent] = semiauto.deriveEncoder
  val jsonDecoder: Decoder[IssueEvent] = semiauto.deriveDecoder

  implicit val persistentEncoder: PersistentEncoder[IssueEvent] =
    jsonEncoder.toPersistenceEncoder
  implicit val persistentDecoder: PersistentDecoder[IssueEvent] =
    jsonDecoder.toPersistenceDecoder
}

final case class IssueState(chatId: ChatId,
                            isResolved: Boolean,
                            description: Description,
                            comments: Chain[Comment]) {
  def handleEvent(e: IssueEvent): Folded[IssueState] = e match {
    case _: IssueEvent.IssueCreated => impossible
    case IssueEvent.IssueCommentAdded(message) =>
      copy(comments = comments.append(message)).next
    case IssueEvent.IssueResolved => copy(isResolved = true).next
  }
}

object IssueState {
  def initial(e: IssueEvent): Folded[IssueState] = e match {
    case IssueEvent.IssueCreated(chatId, description) =>
      IssueState(chatId, isResolved = false, description, Chain.empty).next
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
    case Some(x) => reject(IssueRejection.AlreadyExists)
    case None    => append(IssueCreated(chatId, description))
  }

  def markResolved: I[Unit] = read.flatMap {
    case Some(x) => append(IssueResolved)
    case None    => reject(IssueRejection.NotExists)
  }

  def comment(message: Comment): I[Unit] = read.flatMap {
    case Some(x) => append(IssueCommentAdded(message))
    case None    => reject(IssueRejection.NotExists)
  }
}
