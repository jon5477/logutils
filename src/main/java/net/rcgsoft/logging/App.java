package net.rcgsoft.logging;

import java.net.URL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Hello world!
 *
 */
public class App {
	private static final Logger sLog = LogManager.getLogger(App.class.getName());

	public static void main(String[] args) {
		sLog.info("Hello World!");
		try {
//			int i = 5 / 0;
			URL url = new URL("localhost:9000");
			url.openConnection();
		} catch (Throwable t) {
			sLog.error(t.getLocalizedMessage(), t);
		}
	}
}