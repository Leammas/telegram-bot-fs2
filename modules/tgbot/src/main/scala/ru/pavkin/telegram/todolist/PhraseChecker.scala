package ru.pavkin.telegram.todolist

import cats.Applicative
import cats.implicits._

import scala.language.higherKinds

trait PhraseChecker[F[_]] {

  def isSuspicious(item: Item): F[Boolean]

}

final class NaivePhraseChecker[F[_]: Applicative] extends PhraseChecker[F] {
  private val suspiciousWords = Set("kill", "steal")

  def isSuspicious(item: Item): F[Boolean] =
    item
      .split(" ")
      .toList
      .map(w => suspiciousWords.contains(w))
      .exists(identity)
      .pure[F]
}
