package com.github.leammas.issue.issuetracker

import cats.effect.Sync
import com.github.leammas.issue.common.ChatId

import scala.language.higherKinds
import scala.util.Random

trait Notifications[F[_]] {
  def events: fs2.Stream[F, ChatId]
}

object DummyNotifications {

  def apply[F[_]](implicit F: Sync[F]): Notifications[F] =
    new Notifications[F] {
      def events: fs2.Stream[F, ChatId] =
        fs2.Stream.repeatEval(F.delay(Random.nextLong()))

    }
}
