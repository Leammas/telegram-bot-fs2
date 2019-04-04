package com.github.leammas.state

import aecor.data.{ActionT, EitherK, EventsourcedBehavior}
import aecor.runtime.Eventsourced.Entities
import aecor.runtime.eventsourced.ActionRunner
import cats.arrow.FunctionK
import cats.data.Chain
import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import cats.mtl.ApplicativeAsk
import cats.tagless.FunctorK

object Runtime {

  final case class InnerState[F[_], K, E](value: Ref[F, Map[K, Chain[E]]])
      extends AnyVal

  def runBehaviour[M[_[_]]: FunctorK, F[_], S, K, E, R](
      behaviour: EventsourcedBehavior[EitherK[M, R, ?[_]], F, Option[S], E]
  )(implicit F: Sync[F], AA: ApplicativeAsk[F, InnerState[F, K, E]])
    : Entities.Rejectable[K, M, F, R] = {

    def actionRunner(key: K): ActionRunner[F, Option[S], E] =
      new FunctionK[ActionT[F, Option[S], E, ?], F] {
        def apply[A](fa: ActionT[F, Option[S], E, A]): F[A] = AA.ask.flatMap {
          events =>
            for {
              currentEvents <- events.value.get
                .map(_.getOrElse(key, Chain.empty))
              currentState <- currentEvents
                .foldM(behaviour.create)(behaviour.update)
                .fold(F.raiseError[Option[S]](
                  new RuntimeException("Impossible fold")))(F.pure)
              actionResult <- fa
                .run(currentState, behaviour.update)
                .flatMap(_.fold(F.raiseError[(Chain[E], A)](
                  new RuntimeException("Impossible fold")))(F.pure))
              actionResultEvents = actionResult._1
              _ <- events.value.update(
                s =>
                  s.updated(key,
                            s.get(key)
                              .map(_ ++ actionResultEvents)
                              .getOrElse(actionResultEvents)))
            } yield actionResult._2
        }

      }

    Entities.fromEitherK((key: K) => behaviour.actions.mapK(actionRunner(key)))

  }

}
