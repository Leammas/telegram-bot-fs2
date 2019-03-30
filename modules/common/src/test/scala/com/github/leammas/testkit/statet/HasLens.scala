package com.github.leammas.testkit.statet

import monocle.Lens

trait HasLens[A, B] {
  def lens: Lens[A, B]
}

object HasLens {
  final implicit class HasLensOps[A, B](val l: Lens[A, B]) extends AnyVal {
    def toHasLens: HasLens[A, B] = new HasLens[A, B] {
      val lens: Lens[A, B] = l
    }
  }
}
