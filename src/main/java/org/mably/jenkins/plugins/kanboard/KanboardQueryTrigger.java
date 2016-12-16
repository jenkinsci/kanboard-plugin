package org.mably.jenkins.plugins.kanboard;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.thetransactioncompany.jsonrpc2.client.JSONRPC2Session;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2SessionException;

import antlr.ANTLRException;
import hudson.AbortException;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.BuildableItem;
import hudson.model.Item;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

/** Triggers a build when a new specific Kanboard task has been created. */
public class KanboardQueryTrigger extends Trigger<BuildableItem> {

	private static final Logger LOGGER = Logger.getLogger(KanboardQueryTrigger.class.getName());

	private static final String FINGERPRINT_FILE_NAME = "kanboard-query-trigger-last";

	private final String projectIdentifier;
	private final String query;
	private final String referenceRegexp;
	private final Pattern referencePattern;

	@DataBoundConstructor
	public KanboardQueryTrigger(String cronTabSpec, String projectIdentifier, String query, String referenceRegexp)
			throws ANTLRException {
		super(cronTabSpec);
		this.projectIdentifier = projectIdentifier;
		this.query = query;
		this.referenceRegexp = referenceRegexp;
		if (StringUtils.isNotBlank(referenceRegexp)) {
			this.referencePattern = Pattern.compile(referenceRegexp);
		} else {
			this.referencePattern = null;
		}
	}

	@Override
	public void start(BuildableItem project, boolean newInstance) {
		super.start(project, newInstance);
	}

	@Override
	public void run() {
		JSONObject[] updatedTasks = updatedTasks();
		if (ArrayUtils.isNotEmpty(updatedTasks)) {
			for (int i = 0; i < updatedTasks.length; i++) {
				JSONObject updatedTask = (JSONObject) updatedTasks[i];
				KanboardQueryTriggerCause theCause = new KanboardQueryTriggerCause(updatedTask);
				if (this.job instanceof AbstractProject) {
					AbstractProject theJob = (AbstractProject) this.job;
					String reference = (String) updatedTask.get(Kanboard.REFERENCE);
					StringParameterValue refParamValue = new StringParameterValue(
							KanboardPlugin.KANBOARD_TASKREF_ENVVAR, reference);
					List<ParameterValue> refParamValues = new ArrayList<ParameterValue>();
					refParamValues.add(refParamValue);
					String[] groups = getTaskReferenceGroups(reference);
					if (ArrayUtils.isNotEmpty(groups)) {
						for (int g = 0; g < groups.length; g++) {
							String group = groups[g];
							refParamValue = new StringParameterValue(KanboardPlugin.KANBOARD_TASKREF_ENVVAR + "_" + g,
									group);
							refParamValues.add(refParamValue);
						}
					}
					ParametersAction action = new ParametersAction(refParamValues);
					theJob.scheduleBuild(theJob.getQuietPeriod(), theCause, action);
				} else {
					this.job.scheduleBuild(theCause);
				}
			}
		}
	}

	private JSONObject[] updatedTasks() {

		JSONObject[] updatedTasks = null;

		try {

			boolean debugMode = false;
			PrintStream logger = System.out;

			int lastTriggerTimestamp = getLastTimestamp();
			int newTriggerTimestamp = lastTriggerTimestamp;

			KanboardGlobalConfiguration config = getDescriptor().getGlobalConfiguration();

			JSONRPC2Session session = Utils.initJSONRPCSession(config.getEndpoint(), config.getApiToken(),
					config.getApiTokenCredentialId());

			JSONObject jsonProject = Kanboard.getProjectByIdentifier(session, logger, projectIdentifier, debugMode);
			if (jsonProject == null) {
				throw new RuntimeException(Messages.project_not_found(projectIdentifier));
			}
			String projectId = (String) jsonProject.get(Kanboard.ID);

			JSONArray jsonTasks = Kanboard.searchTasks(session, logger, Integer.valueOf(projectId), query, debugMode);

			List<JSONObject> updatedTasksList = new ArrayList<JSONObject>();
			for (int i = 0; i < jsonTasks.size(); i++) {
				try {
					JSONObject jsonTask = (JSONObject) jsonTasks.get(i);
					String reference = (String) jsonTask.get(Kanboard.REFERENCE);
					String dateCreation = (String) jsonTask.get(Kanboard.DATE_CREATION);
					int currentTimestamp = Integer.parseInt(dateCreation);
					if (checkTaskReference(reference) && (currentTimestamp > lastTriggerTimestamp)) {
						updatedTasksList.add(jsonTask);
					}
					if (currentTimestamp > newTriggerTimestamp) {
						newTriggerTimestamp = currentTimestamp;
					}
				} catch (Exception e) {
					LOGGER.warning(e.getMessage());
				}
			}

			updatedTasks = updatedTasksList.toArray(new JSONObject[updatedTasksList.size()]);

			setLastTimestamp(newTriggerTimestamp);

		} catch (MalformedURLException | AbortException | JSONRPC2SessionException e) {

			throw new RuntimeException(e);

		}

		return updatedTasks;
	}

	private boolean checkTaskReference(String reference) {
		boolean checked;
		if (this.referencePattern == null) {
			checked = true;
		} else {
			Matcher matcher = this.referencePattern.matcher(reference);
			checked = matcher.find();
		}
		return checked;
	}

	private String[] getTaskReferenceGroups(String reference) {
		String[] groups;
		if (this.referencePattern == null) {
			groups = null;
		} else {
			Matcher matcher = this.referencePattern.matcher(reference);
			if (matcher.matches()) {
				groups = new String[matcher.groupCount()];
				for (int i = 0; i < matcher.groupCount(); i++) {
					groups[i] = matcher.group(i + 1);
				}
			} else {
				groups = null;
			}
		}
		return groups;
	}

	public String getProjectIdentifier() {
		return projectIdentifier;
	}

	public String getQuery() {
		return query;
	}

	public String getReferenceRegexp() {
		return referenceRegexp;
	}

	private File getFingerprintFile() {
		return new File(job.getRootDir(), FINGERPRINT_FILE_NAME);
	}

	private int getLastTimestamp() {
		int timestamp;
		try {
			FileInputStream fis = new FileInputStream(getFingerprintFile());
			DataInputStream dis = new DataInputStream(fis);
			timestamp = dis.readInt();
			dis.close();
		} catch (IOException e) {
			LOGGER.info("IOException : " + e.getMessage());
			timestamp = 0;
		}
		return timestamp;
	}

	private void setLastTimestamp(int timestamp) {
		try {
			FileOutputStream fos = new FileOutputStream(getFingerprintFile());
			DataOutputStream dos = new DataOutputStream(fos);
			dos.writeInt(timestamp);
			dos.close();
		} catch (IOException e) {
			LOGGER.warning("IOException : " + e.getMessage());
		}
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	@Extension
	public static final class DescriptorImpl extends TriggerDescriptor {

		static final String QUERY_FIELD = "query";

		@Inject
		KanboardGlobalConfiguration globalConfiguration;

		public DescriptorImpl() {
			super(KanboardQueryTrigger.class);
		}

		@Override
		public boolean isApplicable(Item item) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return Messages.Kanboard_trigger_displayName();
		}

		/**
		 * Performs syntax check.
		 */
		public FormValidation doCheckQuery(@QueryParameter(QUERY_FIELD) String query) {
			return FormValidation.ok();
		}

		/**
		 * @return Actual GlobalConfiguration that contributes to the system
		 *         configuration page.
		 */
		public KanboardGlobalConfiguration getGlobalConfiguration() {
			return this.globalConfiguration;
		}

	}
}