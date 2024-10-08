package it.airgap.beaconsdk.core.internal.blockchain

import androidx.annotation.RestrictTo
import it.airgap.beaconsdk.core.blockchain.Blockchain
import it.airgap.beaconsdk.core.internal.utils.failWithBlockchainNotFound
import it.airgap.beaconsdk.core.internal.utils.getAndDispose
import it.airgap.beaconsdk.core.internal.utils.getOrPutIfNotNull

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class BlockchainRegistry internal constructor(factories: Map<String, () -> Blockchain>) {
    private val factories: MutableMap<String, () -> Blockchain> = factories.toMutableMap()
    private val blockchains: MutableMap<String, Blockchain> = mutableMapOf()

    public fun get(type: String): Blockchain = getOrNull(type) ?: failWithBlockchainNotFound(type)

    public fun getOrNull(type: String): Blockchain? = blockchains.getOrPutIfNotNull(type) {
        factories.getAndDispose(type)?.invoke()
    }
}