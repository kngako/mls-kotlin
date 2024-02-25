package com.github.traderjoe95.mls.protocol.crypto

import arrow.core.raise.Raise
import com.github.traderjoe95.mls.protocol.error.DecryptError
import com.github.traderjoe95.mls.protocol.types.crypto.Aad
import com.github.traderjoe95.mls.protocol.types.crypto.Ciphertext
import com.github.traderjoe95.mls.protocol.types.crypto.EncryptContext
import com.github.traderjoe95.mls.protocol.types.crypto.HpkeCiphertext
import com.github.traderjoe95.mls.protocol.types.crypto.HpkeKeyPair
import com.github.traderjoe95.mls.protocol.types.crypto.HpkePrivateKey
import com.github.traderjoe95.mls.protocol.types.crypto.HpkePublicKey
import com.github.traderjoe95.mls.protocol.types.crypto.KemOutput
import com.github.traderjoe95.mls.protocol.types.crypto.Nonce
import com.github.traderjoe95.mls.protocol.types.crypto.Secret

interface Encrypt {
  fun encryptWithLabel(
    publicKey: HpkePublicKey,
    label: String,
    context: ByteArray,
    plaintext: ByteArray,
  ): HpkeCiphertext

  fun decryptWithLabel(
    keyPair: HpkeKeyPair,
    label: String,
    context: ByteArray,
    ciphertext: HpkeCiphertext,
  ): ByteArray

  fun decryptWithLabel(
    privateKey: HpkePrivateKey,
    label: String,
    context: ByteArray,
    ciphertext: HpkeCiphertext,
  ): ByteArray

  fun encryptAead(
    key: Secret,
    nonce: Nonce,
    aad: Aad,
    plaintext: ByteArray,
  ): Ciphertext

  context(Raise<DecryptError>)
  fun decryptAead(
    key: Secret,
    nonce: Nonce,
    aad: Aad,
    ciphertext: Ciphertext,
  ): ByteArray

  fun export(
    publicKey: HpkePublicKey,
    info: String,
  ): Pair<KemOutput, Secret>

  fun export(
    kemOutput: KemOutput,
    keyPair: HpkeKeyPair,
    info: String,
  ): Secret

  val keyLen: UShort
  val nonceLen: UShort

  abstract class Provider : Encrypt {
    final override fun encryptWithLabel(
      publicKey: HpkePublicKey,
      label: String,
      context: ByteArray,
      plaintext: ByteArray,
    ): HpkeCiphertext = sealBase(publicKey, EncryptContext.create(label, context), Aad.empty, plaintext)

    final override fun decryptWithLabel(
      keyPair: HpkeKeyPair,
      label: String,
      context: ByteArray,
      ciphertext: HpkeCiphertext,
    ): ByteArray =
      openBase(
        ciphertext.kemOutput,
        keyPair,
        EncryptContext.create(label, context),
        Aad.empty,
        ciphertext.ciphertext,
      )

    internal abstract fun sealBase(
      publicKey: HpkePublicKey,
      context: EncryptContext,
      aad: Aad,
      plaintext: ByteArray,
    ): HpkeCiphertext

    internal abstract fun openBase(
      kemOutput: KemOutput,
      keyPair: HpkeKeyPair,
      context: EncryptContext,
      aad: Aad,
      ciphertext: Ciphertext,
    ): ByteArray
  }
}
