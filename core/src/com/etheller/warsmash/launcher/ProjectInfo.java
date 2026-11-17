package com.etheller.warsmash.launcher;

/** Where this port is published. Kept in code rather than in the translated strings so every build names it. */
public final class ProjectInfo {
	public static final String NAME = "W3AndroidEngine";
	public static final String AUTHOR = "nikoltu";
	public static final String SOURCE_URL = "https://github.com/nikoltu/W3AndroidEngine";
	public static final String SOURCE_NAME = "github.com/nikoltu/W3AndroidEngine";
	/** Written into replay and saved game headers. */
	public static final String AUTHORSHIP = NAME + " by " + AUTHOR + " \u2013 " + SOURCE_URL;
	/** The small credit on the map loading screen. */
	public static final String CREDIT = NAME + " \u00b7 " + AUTHOR;

	private ProjectInfo() {
	}
}
