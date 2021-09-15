package it.airgap.beaconsdk.core.internal

import android.content.Context
import androidx.annotation.RestrictTo
import it.airgap.beaconsdk.core.internal.chain.Chain
import it.airgap.beaconsdk.core.internal.crypto.Crypto
import it.airgap.beaconsdk.core.internal.crypto.data.KeyPair
import it.airgap.beaconsdk.core.internal.data.BeaconApplication
import it.airgap.beaconsdk.core.internal.di.CoreDependencyRegistry
import it.airgap.beaconsdk.core.internal.di.DependencyRegistry
import it.airgap.beaconsdk.core.internal.storage.StorageManager
import it.airgap.beaconsdk.core.internal.utils.failWithUninitialized
import it.airgap.beaconsdk.core.internal.utils.toHexString
import it.airgap.beaconsdk.core.storage.SecureStorage
import it.airgap.beaconsdk.core.storage.Storage

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class BeaconSdk(context: Context) {
    public var isInitialized: Boolean = false
        private set

    public val applicationContext: Context = context.applicationContext

    private var _dependencyRegistry: DependencyRegistry? = null
    public val dependencyRegistry: DependencyRegistry
        get() = _dependencyRegistry ?: failWithUninitialized(TAG)

    private var _app: BeaconApplication? = null
    public val app: BeaconApplication
        get() = _app ?: failWithUninitialized(TAG)

    public val beaconId: String
        get() = app.keyPair.publicKey.toHexString().asString()

    public suspend fun init(
        appName: String,
        appIcon: String?,
        appUrl: String?,
        chainFactories: List<Chain.Factory<*>>,
        storage: Storage,
        secureStorage: SecureStorage,
    ) {
        if (isInitialized) return

        _dependencyRegistry = CoreDependencyRegistry(chainFactories, storage, secureStorage)

        val storageManager = dependencyRegistry.storageManager
        val crypto = dependencyRegistry.crypto

        setSdkVersion(storageManager)

        _app = BeaconApplication(
            loadOrGenerateKeyPair(storageManager, crypto),
            appName,
            appIcon,
            appUrl,
        )

        isInitialized = true
    }

    private suspend fun setSdkVersion(storageManager: StorageManager) {
        storageManager.setSdkVersion(BeaconConfiguration.sdkVersion)
    }

    private suspend fun loadOrGenerateKeyPair(storageManager: StorageManager, crypto: Crypto): KeyPair {
        val seed = storageManager.getSdkSecretSeed()
            ?: crypto.guid().getOrThrow().also { storageManager.setSdkSecretSeed(it) }

        return crypto.getKeyPairFromSeed(seed).getOrThrow()
    }

    public companion object {
        internal const val TAG = "BeaconSdk"

        @Suppress("ObjectPropertyName")
        private var _instance: BeaconSdk? = null
        public val instance: BeaconSdk
            get() = _instance ?: failWithUninitialized(TAG)

        internal fun create(context: Context) {
            _instance = BeaconSdk(context)
        }
    }
}