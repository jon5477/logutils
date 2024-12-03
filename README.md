# Log4J2 Utilities
This library provides a multitude of Log4J 2.x Appenders:
- TCP using the [Netty](https://netty.io/) Library
- [Slack](https://slack.com/)
- [Discord](https://discord.com/) by using the Slack Appender
- [Sentry](https://sentry.io/)

This library also provides the following logging layouts:
- [Bunyan](https://github.com/trentm/node-bunyan) - supported at a very basic level
- [Logstash](https://www.elastic.co/logstash)
  - Send logging events to Logstash using TCP by combining the Netty TCP appender with the Logstash layout
- SlackLayout - for sending events to Slack/Discord

## Adding additional context information to a LogEvent
- Utilize the `ContextualMessage` class to add additional key-value mappings to a logging event before sending it to a specific destination

## Built-in Logging Message Queueing
- The Netty TCP Appender will automatically queue messages to the local filesystem if it is unable to deliver messages via TCP. Once network connectivity is restored, these messages are delivered.

## Automatic HTTP Client Selection
- The Slack/Discord Appenders will automatically select a suitable HTTP client based on the dependencies resolved during runtime:
- The priority for HTTP client selection is as follows:
  - [Apache HTTP Client 5.x](https://hc.apache.org/httpcomponents-client-5.3.x/index.html)
  - [Apache HTTP Client 4.x](https://hc.apache.org/httpcomponents-client-4.5.x/index.html)
  - [async-http-client](https://github.com/AsyncHttpClient/async-http-client)
  - [Java HttpClient](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpClient.html) - Fallback, if no other HTTP clients are available

# Requirements
- Java 17 (Java 11 is EOL)
- Log4J 2.x
- Netty 4.x
- Jackson Databind
- Jakarta Activation

# Build Requirements
- Maven 3.6.3 (at a minimum), the latest version is recommended
- Java 17 (we do not provide support for Java 8 and 11 as they are both end-of-life)
