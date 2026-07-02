package com.trading.core.ports;

/**
 * Port for retrieving secrets. Adapters: EnvSecretStore (local), GcpSecretStore (GKE).
 * Never read API keys directly from environment — always go through this interface.
 */
public interface SecretStore {
    String get(String key);
}
