package io.quarkiverse.aesh.runtime;

import jakarta.enterprise.context.Dependent;

import org.aesh.AeshRuntimeRunner;
import org.aesh.command.CommandResult;

import io.quarkus.runtime.QuarkusApplication;

@Dependent
public class AeshRunner implements QuarkusApplication {

    private final AeshRuntimeRunner commandRunner;

    public AeshRunner(AeshRuntimeRunner commandRunner) {
        this.commandRunner = commandRunner;
    }

    @Override
    public int run(String... args) throws Exception {
        try {
            commandRunner.args(args);
            CommandResult result = commandRunner.execute();
            if (result == null || result == CommandResult.SUCCESS) {
                return 0;
            } else {
                return 1;
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        } finally {
            System.out.flush();
            System.err.flush();
        }
    }
}
