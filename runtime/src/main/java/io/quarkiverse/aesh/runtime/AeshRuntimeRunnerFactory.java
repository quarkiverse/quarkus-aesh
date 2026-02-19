package io.quarkiverse.aesh.runtime;

import org.aesh.AeshRuntimeRunner;

/**
 * Factory interface for creating AeshRuntimeRunner instances.
 */
public interface AeshRuntimeRunnerFactory {
    /**
     * Create a new AeshRuntimeRunner instance
     *
     * @return the AeshRuntimeRunner instance
     */
    AeshRuntimeRunner create();
}
