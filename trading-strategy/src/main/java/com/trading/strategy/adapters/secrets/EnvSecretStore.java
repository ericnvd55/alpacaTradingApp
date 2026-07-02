package com.trading.strategy.adapters.secrets;

import com.trading.core.ports.SecretStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Reads secrets from environment variables. Active when NOT on gcp-gke profile.
 * Load your .env before starting: export $(grep -v '^#' .env | xargs)
 */
@Component
@Profile("!gcp-gke")
public class EnvSecretStore implements SecretStore {

    @Override
    public String get(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required secret not found in environment: " + key +
                    ". Ensure .env is loaded before starting the application.");
        }
        return value;
    }
}
