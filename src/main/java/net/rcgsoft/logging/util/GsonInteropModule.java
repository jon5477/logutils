/*********************************************************************
* Copyright (c) 2024 Jon Huang
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/

package net.rcgsoft.logging.util;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.gson.JsonElement;

import net.rcgsoft.logging.util.ser.GsonJsonElementSerializer;

/**
 * GSON interoperability module to bring serialization capabilities of GSON
 * objects to Jackson.
 * 
 * @author Jon Huang
 *
 */
class GsonInteropModule extends SimpleModule {
	private static final long serialVersionUID = 1L;

	GsonInteropModule() {
		super("gson-interop");
		addSerializer(JsonElement.class, new GsonJsonElementSerializer());
	}
}