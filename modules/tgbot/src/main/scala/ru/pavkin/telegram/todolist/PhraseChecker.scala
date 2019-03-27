package ru.pavkin.telegram.todolist

import cats.implicits._
import com.github.leammas.ApplicativeThrowable.ApplicativeThrowable

import scala.language.higherKinds

trait PhraseChecker[F[_]] {

  def isSuspicious(item: Item): F[Boolean]

}

final class NaivePhraseChecker[F[_]](implicit F: ApplicativeThrowable[F])
    extends PhraseChecker[F] {
  private val suspiciousWords = Set("kill", "steal")

  private val panicWords = Set("goroutine")

  def isSuspicious(item: Item): F[Boolean] = {
    val words = item
      .split(" ")
      .toList

    val preprocessedSuccessfully =
      if (words.map(w => panicWords.contains(w)).exists(identity)) {
        F.raiseError[Boolean](new IllegalStateException("Something just wrong"))
      } else {
        true.pure[F]
      }

    val isSuspicious =
      words
        .map(w => suspiciousWords.contains(w))
        .exists(identity)
        .pure[F]

    (preprocessedSuccessfully, isSuspicious).mapN(_ && _)
  }

}
