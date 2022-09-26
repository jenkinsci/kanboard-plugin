package org.mably.jenkins.plugins.kanboard;

import hudson.model.Cause;
import net.minidev.json.JSONObject;

public class KanboardQueryTriggerCause extends Cause {

	private JSONObject task;

	public KanboardQueryTriggerCause(JSONObject task) {
		this.task = task;
	}

	@Override
	public String getShortDescription() {
		String title = String.valueOf(task.get(Kanboard.TITLE));
		return title;
	}

}
