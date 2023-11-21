# Course Project Distributed Systems: Messaging System and Protocols

We developed a messaging system consisting of transfer servers, mailbox servers, monitoring servers, a decentralized naming service and secure communication protocols over TCP/UDP. Among the tools we used were the Java Concurrency API, Java Remote Method Invocation, and the Java Cryptography API.

Using gradle
------------

### Compile & Test

Gradle is the build tool we are using. Here are some instructions:

Compile the project using the gradle wrapper:

    ./gradlew assemble

Compile and run the tests:

    ./gradlew build

### Run the applications

The gradle config config contains several tasks that start application components for you.
You can list them with

    ./gradlew tasks --all

And search for 'Other tasks' starting with `run-`. For example, to run the monitoring server, execute:
(the `--console=plain` flag disables CLI features, like color output, that may break the console output when running a interactive application)

    ./gradlew --console=plain run-monitoring
