package com.gajamitra.alert;

/**
 * The answer every side-effect tool gives the AI service. The graph fails the run on anything but "accepted" or
 * "recorded", so a refused alert is never mistaken for a sent one.
 */
public record ToolResult(String status, String reason) {

	public static ToolResult accepted() {
		return new ToolResult("accepted", null);
	}

	public static ToolResult recorded() {
		return new ToolResult("recorded", null);
	}

	public static ToolResult rejected(String reason) {
		return new ToolResult("rejected", reason);
	}

	public boolean ok() {
		return reason == null;
	}

}
