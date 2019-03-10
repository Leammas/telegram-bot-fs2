package com.github.leammas.issue.issuetracker


import aecor.macros.boopickleWireProtocol
import cats.tagless.autoFunctorK
import boopickle.Default._
import Issue._
import aecor.runtime.akkapersistence.serialization._
import com.github.leammas.issue.common.ChatId
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto
import com.github.leammas.issue.common.JsonPersistence._
import scala.language.higherKinds

final case class IssueId(value: java.util.UUID) extends AnyVal

@boopickleWireProtocol
@autoFunctorK(false)
trait Issue[F[_]] {
  def create(chatId: ChatId, description: Description): F[Either[IssueRejection, Unit]]
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
}

sealed abstract class IssueEvent extends Product with Serializable
object IssueEvent {
  final case class IssueCreated(chatId: Long, description: Description ) extends IssueEvent
  final case class IssueCommentAdded(message: Comment) extends IssueEvent
  final case object IssueResolved extends IssueEvent

  val jsonEncoder: Encoder[IssueEvent] = semiauto.deriveEncoder
  val jsonDecoder: Decoder[IssueEvent] = semiauto.deriveDecoder

  implicit val persistentEncoder: PersistentEncoder[IssueEvent] = jsonEncoder.toPersistenceEncoder
  implicit val persistentDecoder: PersistentDecoder[IssueEvent] = jsonDecoder.toPersistenceDecoder
}