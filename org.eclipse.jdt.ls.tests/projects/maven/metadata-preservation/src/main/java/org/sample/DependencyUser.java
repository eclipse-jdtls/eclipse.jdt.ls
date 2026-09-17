package org.sample;

import org.apache.commons.lang3.StringUtils;

public class DependencyUser {

	private int unused;

	public boolean accepts(String value) {
		return StringUtils.isNotBlank(value);
	}
}
