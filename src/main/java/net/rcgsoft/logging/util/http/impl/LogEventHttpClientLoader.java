/*********************************************************************
* Copyright (c) 2024 Jon Huang
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/

package net.rcgsoft.logging.util.http.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.rcgsoft.logging.util.http.LogEventHttpClient;

/**
 * This class is used for fetching an appropriate asynchronous HTTP/S client
 * implementation based on the dependencies on the classpath/modulepath.
 * 
 * @author Jon Huang
 *
 */
public final class LogEventHttpClientLoader {
	private static final Logger LOGGER = LoggerFactory.getLogger(LogEventHttpClientLoader.class);

	/**
	 * Loads the appropriate {@link LogEventHttpClient} based on the dependencies on
	 * the classpath/modulepath. This will attempt to load the HTTP clients (in
	 * order):
	 * <ul>
	 * <li>Apache HTTP Client 5.x</li>
	 * <li>Async HTTP Client 3.x</li>
	 * <li>Java's built-in HttpClient (Fallback)</li>
	 * </ul>
	 * 
	 * @return The {@link LogEventHttpClient} implementation.
	 */
	public static LogEventHttpClient load() {
		Class<?>[] classes = new Class<?>[] { Hc5LogEventHttpClient.class, AhcLogEventHttpClient.class };
		for (Class<?> clazz : classes) {
			try {
				return (LogEventHttpClient) clazz.getConstructor().newInstance();
			} catch (Throwable t) {
				// log then try next provider
				LOGGER.info("Failed to load {}", clazz.getSimpleName());
			}
		}
		// always fallback to Java HC
		return new JavaLogEventHttpClient();
	}
}