package net.rcgsoft.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Hello world!
 *
 */
public class App {
	private static final Logger sLog = LogManager.getLogger();

	public static void main(String[] args) {
		System.out.println("Hello World!");
		try {
			int i = 5 / 0;
		} catch (Throwable t) {
			sLog.error(t.getLocalizedMessage(), t);
		}
	}
}