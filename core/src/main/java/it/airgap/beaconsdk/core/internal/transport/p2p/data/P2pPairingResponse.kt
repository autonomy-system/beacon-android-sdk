package it.airgap.beaconsdk.core.internal.transport.p2p.data

import androidx.annotation.RestrictTo
import it.airgap.beaconsdk.core.data.P2pPeer
import kotlinx.serialization.Serializable

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Serializable
public data class P2pPairingResponse(
    val id: String,
    val type: String,
    val name: String,
    val version: String,
    val publicKey: String,
    val relayServer: String,
    val icon: String? = null,
    val appUrl: String? = null,
) {
    public fun extractP2PPeer(): P2pPeer =
        P2pPeer(id, name, publicKey, relayServer, version, icon, appUrl)
}