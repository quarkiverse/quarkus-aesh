package io.quarkiverse.aesh.ssh.deployment;

import io.quarkiverse.aesh.deployment.AeshRemoteTransportBuildItem;
import io.quarkiverse.aesh.ssh.runtime.SshServerLifecycle;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.smallrye.health.deployment.spi.HealthBuildItem;

class AeshSshProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("aesh-ssh");
    }

    @BuildStep
    AdditionalBeanBuildItem registerBeans() {
        return AdditionalBeanBuildItem.unremovableOf(SshServerLifecycle.class);
    }

    @BuildStep
    AeshRemoteTransportBuildItem remoteTransport() {
        return new AeshRemoteTransportBuildItem("ssh");
    }

    @BuildStep
    HealthBuildItem addHealthCheck(AeshSshBuildTimeConfig buildTimeConfig) {
        return new HealthBuildItem(
                "io.quarkiverse.aesh.ssh.runtime.health.AeshSshHealthCheck",
                buildTimeConfig.healthEnabled());
    }
}
