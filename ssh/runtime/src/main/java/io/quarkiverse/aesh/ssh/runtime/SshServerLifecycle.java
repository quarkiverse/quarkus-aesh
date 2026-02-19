package io.quarkiverse.aesh.ssh.runtime;

import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.aesh.terminal.Connection;
import org.aesh.terminal.ssh.netty.NettySshTtyBootstrap;
import org.apache.sshd.server.config.keys.AuthorizedKeysAuthenticator;
import org.apache.sshd.server.keyprovider.AbstractGeneratorHostKeyProvider;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.jboss.logging.Logger;

import io.quarkiverse.aesh.runtime.AeshRemoteConnectionHandler;
import io.quarkiverse.aesh.runtime.TransportSessionInfo;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

/**
 * Manages the lifecycle of the SSH server for aesh terminal access.
 * <p>
 * Uses {@link NettySshTtyBootstrap} from aesh-readline's terminal-ssh module
 * to start an SSH server. Each SSH connection is handled by
 * {@link AeshRemoteConnectionHandler}, which sets up a full aesh console
 * with the application's command registry.
 * <p>
 * Note: SSH connections are event-driven; the handler callback returns immediately
 * after setting up event handlers. Connection tracking uses close handlers
 * rather than blocking thread lifetime.
 */
@ApplicationScoped
public class SshServerLifecycle implements TransportSessionInfo {

    private static final Logger LOG = Logger.getLogger(SshServerLifecycle.class);
    private static final String REJECTED_MESSAGE = "Connection rejected: maximum number of sessions reached.\r\n";

    @Inject
    AeshRemoteConnectionHandler connectionHandler;

    @Inject
    AeshSshConfig config;

    private NettySshTtyBootstrap bootstrap;
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private volatile ScheduledExecutorService idleScheduler;

    void onStart(@Observes StartupEvent event) throws Exception {
        if (!config.enabled()) {
            LOG.info("Aesh SSH server is disabled");
            return;
        }

        AbstractGeneratorHostKeyProvider keyProvider = new SimpleGeneratorHostKeyProvider(
                new File(config.hostKeyFile()).toPath());
        keyProvider.setAlgorithm("RSA");

        bootstrap = new NettySshTtyBootstrap()
                .setHost(config.host())
                .setPort(config.port())
                .setKeyPairProvider(keyProvider);

        if (config.password().isPresent()) {
            String expectedPassword = config.password().get();
            bootstrap.setPasswordAuthenticator(
                    (username, password, session) -> expectedPassword.equals(password));
        }

        if (config.authorizedKeysFile().isPresent()) {
            bootstrap.setPublicKeyAuthenticator(
                    new AuthorizedKeysAuthenticator(Paths.get(config.authorizedKeysFile().get())));
            LOG.infof("SSH public key authentication enabled from %s", config.authorizedKeysFile().get());
        }

        if (config.password().isEmpty() && config.authorizedKeysFile().isEmpty()) {
            LOG.warn("Aesh SSH server is running without authentication. " +
                    "Any password will be accepted. Set 'quarkus.aesh.ssh.password' or " +
                    "'quarkus.aesh.ssh.authorized-keys-file' to secure access.");
        }

        int max = config.maxConnections().orElse(0);
        long idleTimeoutMs = config.idleTimeout().map(d -> d.toMillis()).orElse(0L);

        if (idleTimeoutMs > 0) {
            idleScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "aesh-ssh-idle-checker");
                t.setDaemon(true);
                return t;
            });
        }

        bootstrap.start(connection -> handleConnection(connection, max, idleTimeoutMs))
                .get(10, TimeUnit.SECONDS);

        LOG.infof("Aesh SSH server started on %s:%d", config.host(), config.port());
    }

    private void handleConnection(Connection connection, int max, long idleTimeoutMs) {
        // Max connections check with CAS loop
        if (max > 0) {
            int current;
            do {
                current = activeConnections.get();
                if (current >= max) {
                    LOG.warnf("Rejected SSH connection: limit of %d reached", max);
                    connection.stdoutHandler().accept(REJECTED_MESSAGE.codePoints().toArray());
                    connection.close();
                    return;
                }
            } while (!activeConnections.compareAndSet(current, current + 1));
        }

        // Wrap the connection to track close events and idle activity.
        // SSH connections are event-driven (openBlocking() is a no-op), so we
        // cannot use thread lifetime for tracking. The close handler fires when
        // the user disconnects or the session is forcibly closed.
        ScheduledFuture<?>[] idleCheckHolder = new ScheduledFuture<?>[1];
        IdleTrackingConnection tracked = new IdleTrackingConnection(connection, () -> {
            if (max > 0) {
                activeConnections.decrementAndGet();
            }
            if (idleCheckHolder[0] != null) {
                idleCheckHolder[0].cancel(false);
            }
        });

        // Idle timeout setup
        if (idleTimeoutMs > 0 && idleScheduler != null) {
            long checkInterval = Math.max(idleTimeoutMs / 2, 500);
            idleCheckHolder[0] = idleScheduler.scheduleAtFixedRate(() -> {
                if (System.currentTimeMillis() - tracked.getLastActivityMs() > idleTimeoutMs) {
                    LOG.infof("Closing idle SSH session (timeout: %dms)", idleTimeoutMs);
                    tracked.close();
                }
            }, checkInterval, checkInterval, TimeUnit.MILLISECONDS);
        }

        connectionHandler.handle(tracked, "ssh");
    }

    @Override
    public boolean isRunning() {
        return bootstrap != null;
    }

    public int getActiveConnectionCount() {
        return activeConnections.get();
    }

    @Override
    public String getTransportName() {
        return "ssh";
    }

    @Override
    public int getActiveSessionCount() {
        return activeConnections.get();
    }

    @Override
    public int getMaxSessions() {
        return config.maxConnections().orElse(-1);
    }

    void onStop(@Observes ShutdownEvent event) throws Exception {
        if (idleScheduler != null) {
            idleScheduler.shutdownNow();
        }
        if (bootstrap != null) {
            bootstrap.stop().get(5, TimeUnit.SECONDS);
            LOG.info("Aesh SSH server stopped");
        }
    }
}
