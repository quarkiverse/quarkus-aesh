package io.quarkiverse.aesh.runtime;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.aesh.AeshConsoleRunner;
import org.aesh.command.settings.SettingsBuilder;
import org.aesh.terminal.Connection;
import org.jboss.logging.Logger;

import io.quarkus.arc.Arc;

/**
 * CDI bean that sets up aesh command processing on any {@link Connection}.
 * <p>
 * Used by the WebSocket and SSH extensions to provide remote terminal access.
 * Each remote connection gets its own {@link AeshConsoleRunner} with independent
 * readline state, while sharing the same CDI-managed command implementations.
 * <p>
 * This bean depends on {@link CliCommandRegistryFactory}, which is only available
 * in console mode. It should only be used when the application is configured for
 * interactive shell mode.
 */
@ApplicationScoped
public class AeshRemoteConnectionHandler {

    private static final Logger LOG = Logger.getLogger(AeshRemoteConnectionHandler.class);

    @Inject
    CliCommandRegistryFactory registryFactory;

    @Inject
    CliConfig config;

    @Inject
    Instance<CliSettings> customizers;

    @Inject
    @SessionOpened
    Event<AeshSessionEvent> openedEvent;

    @Inject
    @SessionClosed
    Event<AeshSessionEvent> closedEvent;

    private volatile Boolean hasOpenObservers;
    private volatile Boolean hasCloseObservers;

    /**
     * Set up aesh command processing on the given connection.
     * <p>
     * This method may or may not block depending on the connection type.
     * For WebSocket connections, it blocks until close. For SSH connections,
     * it returns immediately (event-driven).
     *
     * @param connection the remote terminal connection (SSH, WebSocket, etc.)
     */
    public void handle(Connection connection) {
        handle(connection, "unknown");
    }

    /**
     * Set up aesh command processing on the given connection.
     * <p>
     * This method may or may not block depending on the connection type.
     * For WebSocket connections, it blocks until close. For SSH connections,
     * it returns immediately (event-driven).
     * <p>
     * The closed event is fired via both a close handler (for event-driven SSH)
     * and a finally block (for blocking WebSocket), with an AtomicBoolean
     * ensuring the event fires exactly once.
     *
     * @param connection the remote terminal connection (SSH, WebSocket, etc.)
     * @param transport the transport type ({@code "ssh"}, {@code "websocket"}, etc.)
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void handle(Connection connection, String transport) {
        String sessionId = UUID.randomUUID().toString();
        fireOpenedEvent(sessionId, transport);

        // Use AtomicBoolean to ensure the closed event fires exactly once,
        // whether triggered by the close handler or the finally block.
        AtomicBoolean closedEventFired = new AtomicBoolean(false);

        // Set close handler for event-driven connections (SSH).
        // For connections wrapped in IdleTrackingConnection, this handler
        // is chained (not overwritten) when AeshConsoleRunner sets its own.
        Consumer<Void> existingCloseHandler = connection.getCloseHandler();
        connection.setCloseHandler(v -> {
            if (closedEventFired.compareAndSet(false, true)) {
                fireClosedEvent(sessionId, transport);
            }
            if (existingCloseHandler != null) {
                existingCloseHandler.accept(v);
            }
        });

        try {
            var registryBuilder = registryFactory.create();

            var settingsBuilder = SettingsBuilder.builder()
                    .enableAlias(config.enableAlias())
                    .enableExport(config.enableExport())
                    .enableMan(config.enableMan())
                    .persistHistory(false)
                    .logging(config.logging());

            for (CliSettings customizer : customizers) {
                customizer.customize(settingsBuilder);
            }

            var settings = settingsBuilder.build();

            AeshConsoleRunner runner = AeshConsoleRunner.builder()
                    .commandRegistryBuilder(registryBuilder)
                    .settings(settings)
                    .connection(connection)
                    .prompt(config.prompt());

            if (config.addExitCommand()) {
                runner.addExitCommand();
            }

            runner.start();
        } catch (Exception e) {
            LOG.error("Error handling remote connection", e);
            connection.close();
        } finally {
            // For blocking connections (WebSocket), fire the event when
            // the method returns. For SSH this fires immediately but the
            // close handler above would have already fired it in that case.
            if (closedEventFired.compareAndSet(false, true)) {
                fireClosedEvent(sessionId, transport);
            }
        }
    }

    private void fireOpenedEvent(String sessionId, String transport) {
        if (hasOpenObservers == null) {
            hasOpenObservers = !Arc.container()
                    .resolveObserverMethods(AeshSessionEvent.class, SessionOpened.Literal.INSTANCE).isEmpty();
        }
        if (hasOpenObservers) {
            openedEvent.fireAsync(new AeshSessionEvent(sessionId, transport, Instant.now()));
        }
    }

    private void fireClosedEvent(String sessionId, String transport) {
        if (hasCloseObservers == null) {
            hasCloseObservers = !Arc.container()
                    .resolveObserverMethods(AeshSessionEvent.class, SessionClosed.Literal.INSTANCE).isEmpty();
        }
        if (hasCloseObservers) {
            closedEvent.fireAsync(new AeshSessionEvent(sessionId, transport, Instant.now()));
        }
    }
}
