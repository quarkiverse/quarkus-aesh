package io.quarkiverse.aesh.tamboui.deployment;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;

public class AeshTamboUIProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("aesh-tamboui");
    }

    @BuildStep
    void registerForNativeImage(BuildProducer<ReflectiveClassBuildItem> reflectiveClasses,
            BuildProducer<ServiceProviderBuildItem> serviceProviders) {

        // Register the Aesh backend provider for ServiceLoader discovery
        serviceProviders.produce(new ServiceProviderBuildItem(
                "dev.tamboui.terminal.BackendProvider",
                "dev.tamboui.backend.aesh.AeshBackendProvider"));

        // Register bridge classes for reflection (needed by aesh's command introspection)
        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(
                "org.aesh.tamboui.TuiCommand",
                "org.aesh.tamboui.TuiAppCommand",
                "org.aesh.tamboui.TuiSupport",
                "org.aesh.tamboui.NonClosingConnection")
                .methods().fields().build());

        // Register tamboui backend classes
        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(
                "dev.tamboui.backend.aesh.AeshBackend",
                "dev.tamboui.backend.aesh.AeshBackendProvider")
                .methods().build());
    }
}
