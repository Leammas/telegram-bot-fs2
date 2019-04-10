package com.github.leammas.issue.issuetracker

import aecor.macros.boopickleWireProtocol
import aecor.runtime.Eventsourced.Entities
import boopickle.Default._
import com.github.leammas.issue.common.ChatId
import com.github.leammas.issue.issuetracker.Issue._
import aecor.macros.boopickle.BoopickleCodec._
import cats.tagless.{Derive, FunctorK}
import scodec.Codec

import scala.language.higherKinds

final case class IssueId(value: java.util.UUID) extends AnyVal

@boopickleWireProtocol
trait Issue[F[_]] {
  def create(chatId: ChatId, description: Description): F[Unit]
  def markResolved: F[Unit]
  def comment(message: Comment): F[Unit]
}

object Issue {
  type Description = String
  type Comment = String
  type Issues[F[_]] =
    Entities.Rejectable[IssueId, Issue, F, IssueRejection]

  implicit val functorK: FunctorK[Issue] = Derive.functorK[Issue]

  implicit val rejectionPickler: boopickle.Pickler[IssueRejection] =
    compositePickler[IssueRejection]
      .addConcreteType[IssueRejection.AlreadyExists.type]
      .addConcreteType[IssueRejection.NotExists.type]

  implicit val rejectionCodec: Codec[IssueRejection] =
    codec[IssueRejection]
}

sealed trait IssueRejection extends Product with Serializable
object IssueRejection {
  case object AlreadyExists extends IssueRejection
  case object NotExists extends IssueRejection
}
