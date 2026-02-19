package io.quarkiverse.aesh.websocket.runtime;

/**
 * Holds the configured WebSocket terminal path, set at build time via the recorder.
 */
public class AeshWebSocketPath {

    private static volatile String path = "/aesh/terminal";

    public static String getPath() {
        return path;
    }

    public static void setPath(String path) {
        AeshWebSocketPath.path = path;
    }
}
