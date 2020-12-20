module net.rcgsoft.logutils {
	exports net.rcgsoft.logging.bunyan;
	exports net.rcgsoft.logging.layout;
	exports net.rcgsoft.logging.logstash;
	exports net.rcgsoft.logging.message;
	exports net.rcgsoft.logging.netty;
	exports net.rcgsoft.logging.sentry;
	exports net.rcgsoft.logging.slack;

	requires transitive com.fasterxml.jackson.databind;
	requires com.fasterxml.jackson.datatype.jsr310;
	requires io.netty.buffer;
	requires io.netty.codec.http;
	requires io.netty.common;
	requires io.netty.handler;
	requires io.netty.transport;
	requires io.sentry;
	requires org.apache.logging.log4j;
	requires transitive org.apache.logging.log4j.core;
	requires org.reactivestreams;

	// Unstable modules
	requires async.http.client;
	requires tape;
}