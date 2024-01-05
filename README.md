# Log4J2 Utilities
This library provides a multitude of Log4J 2.x Appenders:
- [Netty TCP](https://netty.io/)
- [Slack](https://slack.com/)
- [Discord](https://discord.com/) using the Slack Appender
- [Sentry](https://sentry.io/)

This library also provides the following logging layouts:
- [Bunyan](https://github.com/trentm/node-bunyan) - at a very basic level
- [Logstash](https://www.elastic.co/logstash)
  - Send logging events to Logstash using TCP - combine the Netty TCP appender with the Logstash layout
- SlackLayout - for sending events to Slack/Discord

## Adding additional context information to a LogEvent
- Utilize the `ContextualMessage` class to add additional KVs to a logging event before sending it to a specific destination

## Built-in Logging Message Queueing
- The Netty TCP Appender will automatically queue messages to the local filesystem if it is unable to send messages to TCP. Once network connectivity is restored, these messages are delivered.
