# Log4J2 Utilities
This library provides a multitude of Log4J 2.x Appenders:
- TCP using the [Netty](https://netty.io/) Library
- [Slack](https://slack.com/)
- [Discord](https://discord.com/) using the Slack Appender
- [Sentry](https://sentry.io/)

This library also provides the following logging layouts:
- [Bunyan](https://github.com/trentm/node-bunyan) - at a very basic level
- [Logstash](https://www.elastic.co/logstash)
  - Logging events can be sent to Logstash with the TCP protocol - combine the Netty TCP appender with the Logstash layout
- SlackLayout - for sending events to Slack/Discord

## Adding additional context information to a LogEvent
- Utilize the `ContextualMessage` class to add additional key-value mappings to a logging event before sending it to a specific destination

## Built-in Logging Message Queueing
- The Netty TCP Appender will automatically queue messages to the local filesystem if it is unable to deliver messages via TCP. Once network connectivity is restored, these messages are delivered.

## Automatic HTTP Client Selection
- The Slack/Discord Appenders will automatically select a suitable HTTP client based on the dependencies located at runtime:
- The priority for HTTP client selection is as follows:
  - [async-http-client](https://github.com/AsyncHttpClient/async-http-client)
  - [Apache HTTP Client 5.x](https://hc.apache.org/httpcomponents-client-5.3.x/index.html)
  - [Apache HTTP Client 4.x](https://hc.apache.org/httpcomponents-client-4.5.x/index.html)

# Build Requirements
- Maven 3.2.5 (at a minimum), the latest version is recommended
- Java 11 (we do not provide support for Java 8 as it is nearing end-of-life)
