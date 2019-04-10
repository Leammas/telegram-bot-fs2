package ru.pavkin.telegram.todolist

import cats.Applicative
import ru.pavkin.telegram.api.ChatId
import cats.implicits._

import scala.language.higherKinds

trait AdminNotifier[F[_]] {

  def notify(chatId: ChatId): F[Unit]

}

final class LazyAdminNotifier[F[_]: Applicative] extends AdminNotifier[F] {
  def notify(chatId: ChatId): F[Unit] = ().pure[F]
}
