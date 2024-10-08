package it.airgap.beaconsdk.blockchain.tezos.data

import it.airgap.beaconsdk.core.internal.utils.dependencyRegistry
import kotlinx.serialization.Serializable

/**
 * Tezos account data.
 *
 * @property [accountId] The value that identifies the account in Beacon..
 * @property [network] The network on which the account is valid.
 * @property [publicKey] The public key that belongs to the account.
 * @property [address] The account address.
 */
@Serializable
public data class TezosAccount internal constructor(
    public val accountId: String,
    public val network: TezosNetwork,
    public val publicKey: String,
    public val address: String,
) {
    public companion object {}
}

/**
 * Creates a new instance of [TezosAccount] with the specified [publicKey], [address] and [network].
 */
public fun TezosAccount(publicKey: String, address: String, network: TezosNetwork): TezosAccount {
    val accountId = dependencyRegistry.identifierCreator.accountId(address, network).getOrThrow()
    return TezosAccount(accountId, network, publicKey, address)
}