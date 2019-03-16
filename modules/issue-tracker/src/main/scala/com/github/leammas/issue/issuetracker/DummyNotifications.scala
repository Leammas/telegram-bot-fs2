package com.github.leammas.issue.issuetracker

import cats.effect.Sync
import com.github.leammas.issue.common.ChatId

import scala.language.higherKinds
import scala.util.Random

object DummyNotifications {

  def stream[F[_]](implicit F: Sync[F]): fs2.Stream[F, ChatId] =
    fs2.Stream.repeatEval(F.delay(Random.nextLong()))

}