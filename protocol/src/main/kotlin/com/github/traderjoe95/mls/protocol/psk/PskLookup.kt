package com.github.traderjoe95.mls.protocol.psk

import arrow.core.raise.Raise
import arrow.core.raise.recover
import com.github.traderjoe95.mls.protocol.error.PskError
import com.github.traderjoe95.mls.protocol.types.crypto.Secret

interface PskLookup {
  context(Raise<PskError>)
  suspend fun resolvePsk(id: PreSharedKeyId): Secret

  companion object {
    val EMPTY: PskLookup =
      object : PskLookup {
        context(Raise<PskError>)
        override suspend fun resolvePsk(id: PreSharedKeyId): Secret = raise(PskError.PskNotFound(id))
      }

    infix fun PskLookup.delegatingTo(fallback: PskLookup): PskLookup =
      object : PskLookup {
        context(Raise<PskError>)
        override suspend fun resolvePsk(id: PreSharedKeyId): Secret =
          recover(
            block = { this@delegatingTo.resolvePsk(id) },
            recover = { err ->
              when (err) {
                is PskError.PskNotFound -> fallback.resolvePsk(id)
                else -> raise(err)
              }
            },
          )
      }
  }
}
