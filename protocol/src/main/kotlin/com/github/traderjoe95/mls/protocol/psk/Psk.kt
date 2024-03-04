package com.github.traderjoe95.mls.protocol.psk

import arrow.core.raise.Raise
import com.github.traderjoe95.mls.codec.Encodable
import com.github.traderjoe95.mls.codec.Struct2
import com.github.traderjoe95.mls.codec.Struct4
import com.github.traderjoe95.mls.codec.type.DataType
import com.github.traderjoe95.mls.codec.type.EnumT
import com.github.traderjoe95.mls.codec.type.ProtocolEnum
import com.github.traderjoe95.mls.codec.type.V
import com.github.traderjoe95.mls.codec.type.enum
import com.github.traderjoe95.mls.codec.type.opaque
import com.github.traderjoe95.mls.codec.type.struct.Struct2T
import com.github.traderjoe95.mls.codec.type.struct.Struct3T
import com.github.traderjoe95.mls.codec.type.struct.lift
import com.github.traderjoe95.mls.codec.type.struct.member.then
import com.github.traderjoe95.mls.codec.type.struct.struct
import com.github.traderjoe95.mls.codec.type.uint16
import com.github.traderjoe95.mls.codec.type.uint64
import com.github.traderjoe95.mls.codec.util.throwAnyError
import com.github.traderjoe95.mls.protocol.crypto.CipherSuite
import com.github.traderjoe95.mls.protocol.crypto.ICipherSuite
import com.github.traderjoe95.mls.protocol.error.PskError
import com.github.traderjoe95.mls.protocol.group.GroupState
import com.github.traderjoe95.mls.protocol.types.GroupId
import com.github.traderjoe95.mls.protocol.types.crypto.Nonce
import com.github.traderjoe95.mls.protocol.util.hex

enum class PskType(ord: UInt, override val isValid: Boolean = true) : ProtocolEnum<PskType> {
  @Deprecated("This reserved value isn't used by the protocol for now", level = DeprecationLevel.ERROR)
  Reserved(0U, false),

  External(1U),
  Resumption(2U),
  ;

  override val ord: UIntRange = ord..ord

  override fun toString(): String = "$name[${ord.first}]"

  companion object {
    val T: EnumT<PskType> = throwAnyError { enum(upperBound = 0xFFU) }
  }
}

enum class ResumptionPskUsage(ord: UInt, override val isValid: Boolean = true) : ProtocolEnum<ResumptionPskUsage> {
  @Deprecated("This reserved value isn't used by the protocol for now", level = DeprecationLevel.ERROR)
  Reserved(0U, false),

  Application(1U),
  ReInit(2U),
  Branch(3U),
  ;

  override val ord: UIntRange = ord..ord

  companion object {
    val T: EnumT<ResumptionPskUsage> = throwAnyError { enum(upperBound = 0xFFU) }

    val PROTOCOL_RESUMPTION: Set<ResumptionPskUsage> = setOf(ReInit, Branch)
  }
}

sealed interface PreSharedKeyId : Struct2T.Shape<PskType, PreSharedKeyId> {
  val pskType: PskType
  val pskNonce: Nonce

  override fun component1(): PskType = pskType

  override fun component2(): PreSharedKeyId = this

  context(Raise<PskError>)
  fun validate(
    cipherSuite: ICipherSuite,
    inReInit: Boolean,
    inBranch: Boolean,
  ): PreSharedKeyId =
    apply {
      if (pskNonce.size != cipherSuite.hashLen.toUInt()) {
        raise(
          PskError.BadPskNonce(
            this,
            cipherSuite.hashLen.toUInt(),
            pskNonce.size,
          ),
        )
      }
    }

  companion object : Encodable<PreSharedKeyId> {
    override val dataT: DataType<PreSharedKeyId> =
      throwAnyError {
        struct("PreSharedKeyID") {
          it.field("psktype", PskType.T)
            .select<PreSharedKeyId, _>(PskType.T, "psktype") {
              case(PskType.External).then(ExternalPskId.T)
                .case(PskType.Resumption).then(ResumptionPskId.T)
            }
        }.lift { _, preSharedKeyId -> preSharedKeyId }
      }
  }
}

class ExternalPskId(
  val pskId: ByteArray,
  override val pskNonce: Nonce,
) : PreSharedKeyId {
  override val pskType: PskType = PskType.External

  private fun asStruct(): Struct2<ByteArray, Nonce> = Struct2(pskId, pskNonce)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ExternalPskId

    if (!pskId.contentEquals(other.pskId)) return false
    if (pskNonce neq other.pskNonce) return false

    return true
  }

  override fun hashCode(): Int {
    var result = pskType.hashCode()
    result = 31 * result + pskId.contentHashCode()
    result = 31 * result + pskNonce.hashCode()
    return result
  }

  override fun toString(): String = "ExternalPskId(pskId=${pskId.hex}, nonce=${pskId.hex})"

  companion object {
    internal val T: DataType<ExternalPskId> =
      struct("ExternalPskId") {
        it.field("psk_id", opaque[V])
          .field("psk_nonce", Nonce.dataT)
      }.lift(::ExternalPskId, ExternalPskId::asStruct)
  }
}

class ResumptionPskId(
  val usage: ResumptionPskUsage,
  val pskGroupId: GroupId,
  val pskEpoch: ULong,
  override val pskNonce: Nonce,
) : PreSharedKeyId {
  override val pskType: PskType = PskType.Resumption

  context(Raise<PskError>)
  override fun validate(
    cipherSuite: ICipherSuite,
    inReInit: Boolean,
    inBranch: Boolean,
  ): PreSharedKeyId =
    apply {
      super.validate(cipherSuite, inReInit, inBranch)

      if (usage == ResumptionPskUsage.ReInit && !inReInit) raise(PskError.InvalidPskUsage(this))
      if (usage == ResumptionPskUsage.Branch && !inBranch) raise(PskError.InvalidPskUsage(this))
    }

  private fun asStruct(): Struct4<ResumptionPskUsage, GroupId, ULong, Nonce> = Struct4(usage, pskGroupId, pskEpoch, pskNonce)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ResumptionPskId

    if (usage != other.usage) return false
    if (pskGroupId neq other.pskGroupId) return false
    if (pskEpoch != other.pskEpoch) return false
    if (pskNonce neq other.pskNonce) return false

    return true
  }

  override fun hashCode(): Int {
    var result = usage.hashCode()
    result = 31 * result + pskGroupId.hashCode
    result = 31 * result + pskEpoch.hashCode()
    result = 31 * result + pskNonce.hashCode
    return result
  }

  override fun toString(): String {
    return "ResumptionPskId(pskGroupId=$pskGroupId, pskEpoch=$pskEpoch, pskNonce=${pskNonce.hex})"
  }

  companion object {
    internal val T: DataType<ResumptionPskId> =
      struct("ResumptionPskId") {
        it.field("usage", ResumptionPskUsage.T)
          .field("psk_group_id", GroupId.dataT)
          .field("psk_epoch", uint64.asULong)
          .field("psk_nonce", Nonce.dataT)
      }.lift(::ResumptionPskId, ResumptionPskId::asStruct)

    fun reInit(
      resumptionEpoch: GroupState.Suspended,
      cipherSuite: CipherSuite,
    ): ResumptionPskId =
      ResumptionPskId(
        ResumptionPskUsage.ReInit,
        resumptionEpoch.groupId,
        resumptionEpoch.epoch,
        cipherSuite.generateNonce(cipherSuite.hashLen),
      )

    fun branch(resumptionEpoch: GroupState.Active): ResumptionPskId =
      ResumptionPskId(
        ResumptionPskUsage.Branch,
        resumptionEpoch.groupId,
        resumptionEpoch.epoch,
        resumptionEpoch.generateNonce(resumptionEpoch.cipherSuite.hashLen),
      )
  }
}

data class PskLabel(
  val pskId: PreSharedKeyId,
  val index: UShort,
  val count: UShort,
) : Struct3T.Shape<PreSharedKeyId, UShort, UShort> {
  constructor(pskId: PreSharedKeyId, index: Int, count: Int) : this(pskId, index.toUShort(), count.toUShort())

  companion object : Encodable<PskLabel> {
    override val dataT: DataType<PskLabel> =
      struct("PSKLabel") {
        it.field("pskId", PreSharedKeyId.dataT)
          .field("index", uint16.asUShort)
          .field("count", uint16.asUShort)
      }.lift(::PskLabel)
  }
}