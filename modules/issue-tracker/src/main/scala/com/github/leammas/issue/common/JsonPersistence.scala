package com.github.leammas.issue.common

import java.nio.ByteBuffer

import aecor.runtime.akkapersistence.serialization.PersistentDecoder.DecodingResult
import aecor.runtime.akkapersistence.serialization.{
  DecodingFailure,
  PersistentDecoder,
  PersistentEncoder,
  PersistentRepr
}
import io.circe.{Decoder, Encoder, Printer}
import cats.syntax.either._
import cats.syntax.option._

object JsonPersistence {

  final implicit class PersistedJsonEncoder[T](val e: Encoder[T])
      extends AnyVal {
    def toPersistenceEncoder: PersistentEncoder[T] = new PersistentEncoder[T] {
      def encode(a: T): PersistentRepr =
        PersistentRepr("", e(a).pretty(Printer.noSpaces).getBytes())
    }
  }

  final implicit class PersistedJsonDecoder[T](val e: Decoder[T])
      extends AnyVal {
    def toPersistenceDecoder: PersistentDecoder[T] = new PersistentDecoder[T] {

      def decode(repr: PersistentRepr): DecodingResult[T] = {
        implicit val decoder: Decoder[T] = e
        io.circe.jawn
          .decodeByteBuffer(ByteBuffer.wrap(repr.payload))
          .leftMap(error => DecodingFailure(error.getMessage, error.some))
      }
    }
  }

}
