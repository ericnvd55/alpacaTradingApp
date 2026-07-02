package com.trading.strategy.adapters.secrets;

import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.trading.core.ports.SecretStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Reads secrets from GCP Secret Manager. Active under gcp-gke profile.
 * Secret names must match environment variable names (e.g. ALPACA_API_KEY).
 */
@Component
@Profile("gcp-gke")
public class GcpSecretStore implements SecretStore {

    private final String projectId;
    private final SecretManagerServiceClient client;

    public GcpSecretStore(
            @Value("${gcp.project-id}") String projectId,
            SecretManagerServiceClient client) {
        this.projectId = projectId;
        this.client = client;
    }

    @Override
    public String get(String key) {
        SecretVersionName name = SecretVersionName.of(projectId, key, "latest");
        AccessSecretVersionResponse response = client.accessSecretVersion(name);
        return response.getPayload().getData().toStringUtf8();
    }
}
