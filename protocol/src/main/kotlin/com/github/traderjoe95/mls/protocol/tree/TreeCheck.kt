package com.github.traderjoe95.mls.protocol.tree

import arrow.core.raise.Raise
import arrow.core.raise.nullable
import com.github.traderjoe95.mls.protocol.app.ApplicationCtx
import com.github.traderjoe95.mls.protocol.crypto.ICipherSuite
import com.github.traderjoe95.mls.protocol.error.LeafNodeCheckError
import com.github.traderjoe95.mls.protocol.error.TreeCheckError
import com.github.traderjoe95.mls.protocol.group.GroupContext
import com.github.traderjoe95.mls.protocol.types.tree.Node
import com.github.traderjoe95.mls.protocol.types.tree.ParentNode

context(ApplicationCtx<Identity>, ICipherSuite, Raise<TreeCheckError>)
suspend fun <Identity : Any> RatchetTree.check(groupContext: GroupContext) {
  treeHash.let { th ->
    if (th.contentEquals(groupContext.treeHash).not()) {
      raise(TreeCheckError.BadTreeHash(groupContext.treeHash, th))
    }
  }

  checkParentHashCoverage()

  nonBlankParentNodeIndices.forEach { parentIdx ->
    val parentNode = parentNode(parentIdx)

    parentNode.checkUnmergedLeaves(parentIdx)

    (nonBlankLeafNodeIndices + nonBlankParentNodeIndices).filter {
      it != parentIdx && node(it).encryptionKey.eq(parentNode.encryptionKey)
    }.sorted().let {
      if (it.isNotEmpty()) raise(LeafNodeCheckError.DuplicateEncryptionKey(it))
    }
  }

  nonBlankLeafIndices.forEach {
    leafNode(it).validate(this@check, groupContext, it)
  }
}

context(ICipherSuite, Raise<TreeCheckError>)
private fun RatchetTree.checkParentHashCoverage() {
  val phCoverage = mutableMapOf<NodeIndex, UInt>()

  leaves.zipWithLeafIndex().mapNotNull { (l, leafIdx) ->
    nullable { l.bind() to leafIdx }
  }.forEach { (leaf, leafIdx) ->
    var currentRefNode: Node = leaf
    var currentNode = leafIdx.nodeIndex

    while (currentNode != root) {
      currentNode = currentNode.parent

      if (currentNode.isBlank && currentNode != root) {
        continue
      } else if (currentNode.isBlank) {
        break
      }

      val ph = parentHash(currentNode, leafIdx)

      if (ph.value.contentEquals(currentRefNode.parentHash?.value)) {
        phCoverage.compute(currentNode) { _, value ->
          (value ?: 0U) + 1U
        }

        currentRefNode = parentNode(currentNode)
      } else {
        break
      }
    }
  }

  nonBlankParentNodeIndices.forEach { parentIdx ->
    if (phCoverage.getOrDefault(parentIdx, 0U) != 1U) raise(TreeCheckError.NotParentHashValid(parentIdx))
  }
}

context(ICipherSuite, RatchetTree, Raise<TreeCheckError>)
private fun ParentNode.checkUnmergedLeaves(parentIdx: NodeIndex) {
  unmergedLeaves.forEach { leafIdx ->
    if (leafIdx.isBlank) raise(TreeCheckError.BadUnmergedLeaf(parentIdx, leafIdx, "Leaf node is blank"))
    if (leafIdx.isInSubtreeOf(parentIdx).not()) {
      raise(
        TreeCheckError.BadUnmergedLeaf(
          parentIdx,
          leafIdx,
          "Not a descendant",
        ),
      )
    }

    var currentNode = leafIdx.parent
    while (currentNode != parentIdx) {
      if (currentNode.isBlank.not() && leafIdx !in parentNode(currentNode).unmergedLeaves) {
        raise(
          TreeCheckError.BadUnmergedLeaf(
            parentIdx,
            leafIdx,
            "Not in unmerged leaf of intermediate node $currentNode",
          ),
        )
      }
      currentNode = currentNode.parent
    }
  }
}
