# Quarkus Aesh

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.aesh/quarkus-aesh?logo=apache-maven&style=flat-square)](https://central.sonatype.com/artifact/io.quarkiverse.aesh/quarkus-aesh-parent)

[Aesh](https://github.com/aeshell/aesh) is a Java library for building interactive command line applications with option parsing, tab completion, command grouping, and an interactive shell (REPL) mode. This Quarkus extension integrates Aesh with the Quarkus framework, providing CDI support, build-time validation, native image compatibility, and optional remote terminal access via SSH and WebSocket.

## Features

- **Two execution modes**: interactive shell (REPL) with tab completion and command history, or single-command CLI
- **Auto-detection**: mode is automatically resolved based on command annotations
- **CDI integration**: commands, completers, validators, and converters are CDI beans with full `@Inject` support
- **Build-time validation**: duplicate command names, missing annotations, and conflicting configurations are caught at build time
- **Remote terminal access**: optional SSH and WebSocket sub-extensions for browser-based or SSH client access
- **Dev UI**: commands page, session monitoring, and embedded terminal in Quarkus Dev UI
- **Native image support**: automatic reflection registration for GraalVM native builds
- **TamboUI integration**: optional TUI framework support via the tamboui sub-extension

## Modules

| Module | Artifact | Description |
|--------|----------|-------------|
| Core | `quarkus-aesh` | Command discovery, CDI integration, mode detection |
| SSH | `quarkus-aesh-ssh` | SSH server for remote terminal access |
| WebSocket | `quarkus-aesh-websocket` | WebSocket endpoint with browser-based terminal |
| TamboUI | `quarkus-aesh-tamboui` | TamboUI TUI framework integration |

## Getting Started

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.quarkiverse.aesh</groupId>
    <artifactId>quarkus-aesh</artifactId>
    <version>${quarkus-aesh.version}</version>
</dependency>
```

### Single command (runtime mode)

A single `@CommandDefinition` class is automatically used as the entry point:

```java
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;

@CommandDefinition(name = "hello", description = "Greet someone")
public class HelloCommand implements Command<CommandInvocation> {

    @Option(shortName = 'n', name = "name", defaultValue = "World")
    private String name;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        invocation.println("Hello " + name + "!");
        return CommandResult.SUCCESS;
    }
}
```

```shell
$ java -jar myapp.jar --name=Quarkus
Hello Quarkus!
```

### Multiple commands (console mode)

With multiple independent commands, the extension auto-detects console mode and starts an interactive REPL:

```java
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;

@CommandDefinition(name = "greet", description = "Greet someone")
public class GreetCommand implements Command<CommandInvocation> {

    @Option(shortName = 'n', name = "name", defaultValue = "World")
    private String name;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        invocation.println("Hello " + name + "!");
        return CommandResult.SUCCESS;
    }
}

@CommandDefinition(name = "info", description = "Show system info")
public class InfoCommand implements Command<CommandInvocation> {

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        invocation.println("Java: " + System.getProperty("java.version"));
        return CommandResult.SUCCESS;
    }
}
```

```shell
$ java -jar myapp.jar
[quarkus]$ greet --name=Aesh
Hello Aesh!
[quarkus]$ info
Java: 21.0.1
[quarkus]$ exit
```

### Remote access

Add SSH or WebSocket sub-extensions for remote terminal access:

```xml
<!-- SSH terminal on port 2222 -->
<dependency>
    <groupId>io.quarkiverse.aesh</groupId>
    <artifactId>quarkus-aesh-ssh</artifactId>
    <version>${quarkus-aesh.version}</version>
</dependency>

<!-- Browser-based terminal at /aesh/index.html -->
<dependency>
    <groupId>io.quarkiverse.aesh</groupId>
    <artifactId>quarkus-aesh-websocket</artifactId>
    <version>${quarkus-aesh.version}</version>
</dependency>
```

## Documentation

The full documentation is available at <https://docs.quarkiverse.io/quarkus-aesh/dev/index.html> and maintained in the [`docs/`](docs/) directory of this repository.

## Contributing

Contributions are welcome. Please open issues and pull requests on this repository.

## License

This project is licensed under the [Apache License 2.0](LICENSE).
