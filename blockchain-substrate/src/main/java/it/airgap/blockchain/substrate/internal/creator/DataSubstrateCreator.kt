package it.airgap.blockchain.substrate.internal.creator

import it.airgap.beaconsdk.core.data.Permission
import it.airgap.beaconsdk.core.internal.blockchain.creator.DataBlockchainCreator
import it.airgap.beaconsdk.core.internal.storage.StorageManager
import it.airgap.beaconsdk.core.internal.utils.IdentifierCreator
import it.airgap.beaconsdk.core.internal.utils.asHexString
import it.airgap.beaconsdk.core.internal.utils.currentTimestamp
import it.airgap.beaconsdk.core.internal.utils.failWithIllegalState
import it.airgap.beaconsdk.core.message.PermissionBeaconRequest
import it.airgap.beaconsdk.core.message.PermissionBeaconResponse
import it.airgap.blockchain.substrate.data.SubstrateAppMetadata
import it.airgap.blockchain.substrate.data.SubstratePermission
import it.airgap.blockchain.substrate.internal.utils.failWithUnknownMessage
import it.airgap.blockchain.substrate.internal.wallet.SubstrateWallet
import it.airgap.blockchain.substrate.message.request.PermissionSubstrateRequest
import it.airgap.blockchain.substrate.message.response.PermissionSubstrateResponse

internal class DataSubstrateCreator(
    private val wallet: SubstrateWallet,
    private val storageManager: StorageManager,
    private val identifierCreator: IdentifierCreator,
) : DataBlockchainCreator {

    override suspend fun extractPermission(
        request: PermissionBeaconRequest,
        response: PermissionBeaconResponse,
    ): Result<List<Permission>> =
        runCatching {
            if (request !is PermissionSubstrateRequest) failWithUnknownMessage(request)
            if (response !is PermissionSubstrateResponse) failWithUnknownMessage(response)

            response.accounts.map { account ->
                val address = wallet.address(account.publicKey, account.addressPrefix).getOrThrow()
                val accountId = identifierCreator.accountId(address, account.network).getOrThrow()
                val appMetadata = storageManager.findInstanceAppMetadata<SubstrateAppMetadata> { it.senderId == request.senderId } ?: failWithAppMetadataNotFound()

                SubstratePermission(
                    accountId,
                    identifierCreator.senderId(request.origin.id.asHexString().toByteArray()).getOrThrow(),
                    connectedAt = currentTimestamp(),
                    appMetadata,
                    response.scopes,
                    account,
                )
            }
        }

    private fun failWithAppMetadataNotFound(): Nothing = failWithIllegalState("Permission could not be extracted, matching appMetadata not found.")
}