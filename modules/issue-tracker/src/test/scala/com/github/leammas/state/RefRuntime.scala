package com.github.leammas.state

import aecor.data.{ActionT, EitherK, EventsourcedBehavior}
import aecor.runtime.Eventsourced.Entities
import aecor.runtime.eventsourced.ActionRunner
import cats.arrow.FunctionK
import cats.data.Chain
import cats.effect.{IO, LiftIO, Sync}
import cats.effect.concurrent.Ref
import cats.implicits._
import cats.mtl.ApplicativeAsk
import cats.tagless.FunctorK

object RefRuntime {

  // fair enough concurrency or move to mvar?
  final case class InnerState[K, E](value: Ref[IO, Map[K, Chain[E]]])
      extends AnyVal

  final class Runner[F[_], K] {
    def apply[M[_[_]]: FunctorK, S, E, R](
        behaviour: EventsourcedBehavior[EitherK[M, R, ?[_]], F, Option[S], E]
    )(implicit F: Sync[F],
      Flift: LiftIO[F],
      AA: ApplicativeAsk[F, InnerState[K, E]])
      : Entities.Rejectable[K, M, F, R] = {

      def actionRunner(key: K): ActionRunner[F, Option[S], E] =
        new FunctionK[ActionT[F, Option[S], E, ?], F] {
          def apply[A](fa: ActionT[F, Option[S], E, A]): F[A] = AA.ask.flatMap {
            events =>
              for {
                currentEvents <- Flift
                  .liftIO(events.value.get)
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
                _ <- Flift.liftIO(events.value.update(
                  s =>
                    s.updated(key,
                              s.get(key)
                                .map(_ ++ actionResultEvents)
                                .getOrElse(actionResultEvents))))
              } yield actionResult._2
          }

        }

      Entities.fromEitherK((key: K) =>
        behaviour.actions.mapK(actionRunner(key)))

    }
  }

  def apply[F[_], K]: Runner[F, K] = new Runner[F, K]

}
