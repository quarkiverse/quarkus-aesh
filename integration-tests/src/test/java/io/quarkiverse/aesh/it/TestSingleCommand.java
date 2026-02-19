package io.quarkiverse.aesh.it;

import static io.quarkiverse.aesh.it.AeshTestUtils.createConfig;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusProdModeTest;

public class TestSingleCommand {

    @RegisterExtension
    static final QuarkusProdModeTest config = createConfig("hello-app", HelloCommand.class)
            .setCommandLineParameters("--name=Aesh");

    @Test
    public void testHelloCommand() {
        Assertions.assertThat(config.getStartupConsoleOutput()).containsOnlyOnce("Hello Aesh!");
        Assertions.assertThat(config.getExitCode()).isZero();
    }
}
