package com.etheller.warsmash.launcher;

import java.io.IOException;

/** A map start that failed for one of the reasons a {@link MapIssue} names; its message is the issue's sentence. */
public class MapIssueException extends IOException {
	private final MapIssue issue;

	public MapIssueException(final MapIssue issue, final String mapFilename) {
		super(issue.english + ((mapFilename == null) || mapFilename.isEmpty() ? "" : " (" + mapFilename + ")"));
		this.issue = issue;
	}

	public MapIssue issue() {
		return this.issue;
	}
}
