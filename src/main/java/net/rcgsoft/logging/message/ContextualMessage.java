/*********************************************************************
* Copyright (c) 2024 Jon Huang
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/

package net.rcgsoft.logging.message;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.message.ParameterizedMessage;

/**
 * A {@link ParameterizedMessage} with context information.
 * 
 * @author Jon Huang
 *
 */
public class ContextualMessage extends ParameterizedMessage {
	private static final long serialVersionUID = 1116169611431210422L;
	private final Map<String, Object> context = new HashMap<>();
	private final List<String> tags = new ArrayList<>();

	public ContextualMessage(final String messagePattern, final Object[] arguments, final Throwable throwable) {
		super(messagePattern, arguments, throwable);
	}

	public ContextualMessage(final String messagePattern, final Object... arguments) {
		super(messagePattern, arguments);
	}

	public ContextualMessage(final String messagePattern, final Object arg) {
		super(messagePattern, arg);
	}

	public ContextualMessage(final String messagePattern, final Object arg0, final Object arg1) {
		super(messagePattern, arg0, arg1);
	}

	public Map<String, Object> getContext() {
		return Collections.unmodifiableMap(context);
	}

	public ContextualMessage withContext(Map<String, Object> context) {
		Objects.requireNonNull(context, "context cannot be null");
		this.context.putAll(context);
		return this;
	}

	public List<String> getTags() {
		return Collections.unmodifiableList(this.tags);
	}

	public ContextualMessage addTag(String tag) {
		Objects.requireNonNull(tag, "tag cannot be null");
		this.tags.add(tag);
		return this;
	}

	public ContextualMessage addTags(Collection<String> tags) {
		Objects.requireNonNull(tags, "tags cannot be null");
		this.tags.addAll(tags);
		return this;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(context, tags);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (!(obj instanceof ContextualMessage)) {
			return false;
		}
		ContextualMessage other = (ContextualMessage) obj;
		return Objects.equals(context, other.context) && Objects.equals(tags, other.tags);
	}

	@Override
	public String toString() {
		return "ContextualMessage [context=" + context + ", tags=" + tags + ", getFormat()=" + getFormat()
				+ ", getParameters()=" + Arrays.toString(getParameters()) + ", getThrowable()=" + getThrowable() + "]";
	}
}