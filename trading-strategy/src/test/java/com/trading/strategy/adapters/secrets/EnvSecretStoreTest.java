package com.trading.strategy.adapters.secrets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvSecretStoreTest {

    private final EnvSecretStore store = new EnvSecretStore();

    @Test
    void returnsValueForPresentKey() {
        // PATH is guaranteed to exist in every OS environment
        assertThat(store.get("PATH")).isNotBlank();
    }

    @Test
    void throwsForMissingKey() {
        assertThatThrownBy(() -> store.get("NONEXISTENT_KEY_XYZ_12345"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NONEXISTENT_KEY_XYZ_12345");
    }
}
