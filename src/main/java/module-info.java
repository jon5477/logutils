module net.rcgsoft.logutils {
	exports com.squareup.tape2;
	exports net.rcgsoft.logging.bunyan;
	exports net.rcgsoft.logging.layout;
	exports net.rcgsoft.logging.logstash;
	exports net.rcgsoft.logging.message;
	exports net.rcgsoft.logging.netty;
	exports net.rcgsoft.logging.sentry;
	exports net.rcgsoft.logging.slack;
	exports net.rcgsoft.logging.util;
	exports net.rcgsoft.logging.util.http;
	exports net.rcgsoft.logging.util.ser;

	opens com.squareup.tape2;
	opens net.rcgsoft.logging.bunyan;
	opens net.rcgsoft.logging.layout;
	opens net.rcgsoft.logging.logstash;
	opens net.rcgsoft.logging.message;
	opens net.rcgsoft.logging.netty;
	opens net.rcgsoft.logging.sentry;
	opens net.rcgsoft.logging.slack;
	opens net.rcgsoft.logging.util;
	opens net.rcgsoft.logging.util.http;
	opens net.rcgsoft.logging.util.ser;

	requires transitive com.fasterxml.jackson.databind;
	requires com.fasterxml.jackson.datatype.jsr310;
	requires static com.google.gson;
	requires io.netty.buffer;
	requires io.netty.codec.http;
	requires io.netty.common;
	requires io.netty.handler;
	requires io.netty.transport;
	requires static org.apache.httpcomponents.client5.httpclient5;
	requires static org.apache.httpcomponents.core5.httpcore5;
	requires static org.apache.httpcomponents.httpasyncclient;
	requires static org.apache.httpcomponents.httpclient;
	requires static org.apache.httpcomponents.httpcore;
	requires static org.apache.httpcomponents.httpcore.nio;
	requires org.apache.logging.log4j;
	requires transitive org.apache.logging.log4j.core;
	requires org.jspecify;
	requires org.slf4j;

	// Unstable modules
	requires static async.http.client;
	requires static sentry;
}