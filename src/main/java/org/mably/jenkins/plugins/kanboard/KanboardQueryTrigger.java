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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.inject.Inject;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.thetransactioncompany.jsonrpc2.client.JSONRPC2Session;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2SessionException;

import antlr.ANTLRException;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
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
	private static final String JSON_TASK_GROUPS = "_groups_";

	private final String crontabSpec;
	private final String projectIdentifier;
	private final String query;
	private final String referenceRegexp;

	@DataBoundConstructor
	public KanboardQueryTrigger(String crontabSpec, String projectIdentifier, String query, String referenceRegexp)
			throws ANTLRException {
		super(Util.replaceMacro(crontabSpec, Utils.getNodeEnvVars()));
		this.crontabSpec = crontabSpec;
		this.projectIdentifier = projectIdentifier;
		this.query = query;
		this.referenceRegexp = referenceRegexp;
	}

	@Override
	public void run() {
		JSONObject[] foundTasks = queryTasks();
		if (ArrayUtils.isNotEmpty(foundTasks)) {
			for (int i = 0; i < foundTasks.length; i++) {
				JSONObject task = (JSONObject) foundTasks[i];
				KanboardQueryTriggerCause theCause = new KanboardQueryTriggerCause(task);
				if (this.job instanceof AbstractProject) {
					AbstractProject theJob = (AbstractProject) this.job;
					String reference = String.valueOf(task.get(Kanboard.REFERENCE));
					StringParameterValue refParamValue = new StringParameterValue(
							KanboardPlugin.KANBOARD_TASKREF_ENVVAR, reference);
					List<ParameterValue> refParamValues = new ArrayList<ParameterValue>();
					refParamValues.add(refParamValue);
					String[] groups = (String[]) task.get(JSON_TASK_GROUPS);
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

	private JSONObject[] queryTasks() {
		KanboardGlobalConfiguration config = getDescriptor().getGlobalConfiguration();
		return queryTasks(config, this.projectIdentifier, this.query, this.referenceRegexp, getFingerprintFile());
	}

	private static JSONObject[] queryTasks(KanboardGlobalConfiguration config, String projectIdentifier, String query,
			String referenceRegexp, File fingerprintFile) {

		JSONObject[] foundTasks = null;

		try {

			EnvVars envVars = Utils.getNodeEnvVars();

			boolean debugMode = config.isDebugMode();
			PrintStream logger = System.out;

			JSONRPC2Session session = Utils.initJSONRPCSession(config.getEndpoint(), config.getApiToken(),
					config.getApiTokenCredentialId());

			String projectIdentifierValue = Util.replaceMacro(projectIdentifier, envVars);
			JSONObject jsonProject = Kanboard.getProjectByIdentifier(session, logger, projectIdentifierValue,
					debugMode);
			if (jsonProject == null) {
				throw new RuntimeException(Messages.project_not_found(projectIdentifierValue));
			}
			Object projectId = jsonProject.get(Kanboard.ID);

			String queryValue = Util.replaceMacro(query, envVars);
			JSONArray jsonTasks = Kanboard.searchTasks(session, logger, projectId, queryValue,
					debugMode);

			Pattern referencePattern = getReferencePattern(referenceRegexp, envVars);

			int lastTriggerTimestamp;
			if (fingerprintFile == null) { // In case of query testing
				lastTriggerTimestamp = 0;
			} else {
				lastTriggerTimestamp = getLastTimestamp(fingerprintFile);
			}
			int newTriggerTimestamp = lastTriggerTimestamp;

			List<JSONObject> foundTasksList = new ArrayList<JSONObject>();
			for (int i = 0; i < jsonTasks.size(); i++) {
				try {
					JSONObject jsonTask = (JSONObject) jsonTasks.get(i);
					String reference = String.valueOf(jsonTask.get(Kanboard.REFERENCE));
					String[] refGroups = getTaskRefMatchGroups(reference, referencePattern);
					if (refGroups != null) {
						String dateMoved = String.valueOf(jsonTask.get(Kanboard.DATE_MOVED));
						int currentTimestamp = Integer.parseInt(dateMoved);
						if (currentTimestamp > lastTriggerTimestamp) {
							jsonTask.put(JSON_TASK_GROUPS, refGroups);
							foundTasksList.add(jsonTask);
						}
						if (currentTimestamp > newTriggerTimestamp) {
							newTriggerTimestamp = currentTimestamp;
						}
					}
				} catch (Exception e) {
					LOGGER.warning(e.getMessage());
				}
			}

			foundTasks = foundTasksList.toArray(new JSONObject[foundTasksList.size()]);

			if (fingerprintFile != null) {
				setLastTimestamp(newTriggerTimestamp, fingerprintFile);
			}

		} catch (MalformedURLException | AbortException | JSONRPC2SessionException e) {

			throw new RuntimeException(e);

		}

		return foundTasks;
	}

	private static Pattern getReferencePattern(String referenceRegexp, EnvVars envVars) {
		Pattern referencePattern;
		String referenceRegexpValue = Util.replaceMacro(referenceRegexp, envVars);
		if (StringUtils.isBlank(referenceRegexpValue)) {
			referencePattern = null;
		} else {
			try {
				referencePattern = Pattern.compile(referenceRegexpValue);
			} catch (PatternSyntaxException e) {
				referencePattern = null;
				LOGGER.log(Level.FINE, "Failed to compile the task reference pattern: " + referenceRegexpValue, e);
			}
		}
		return referencePattern;
	}

	private static String[] getTaskRefMatchGroups(String reference, Pattern refPattern) {
		String[] groups;
		if (refPattern == null) {
			groups = new String[0];
		} else {
			Matcher matcher = refPattern.matcher(reference);
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

	public String getCrontabSpec() {
		return crontabSpec;
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

	private static int getLastTimestamp(File fingerprintFile) {
		int timestamp;
		try {
			FileInputStream fis = new FileInputStream(fingerprintFile);
			DataInputStream dis = new DataInputStream(fis);
			timestamp = dis.readInt();
			dis.close();
		} catch (IOException e) {
			LOGGER.info("IOException : " + e.getMessage());
			timestamp = 0;
		}
		return timestamp;
	}

	private static void setLastTimestamp(int timestamp, File fingerprintFile) {
		try {
			FileOutputStream fos = new FileOutputStream(fingerprintFile);
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

		private static final String PROJECTID_FIELD = "projectIdentifier";
		private static final String QUERY_FIELD = "query";
		private static final String REFREGEXP_FIELD = "referenceRegexp";

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
		 * Performs Kanboard query check.
		 */
		public FormValidation doTestQuery(@QueryParameter(PROJECTID_FIELD) final String projectIdentifier,
				@QueryParameter(QUERY_FIELD) final String query,
				@QueryParameter(REFREGEXP_FIELD) final String referenceRegexp) {
			try {
				JSONObject[] foundTasks = queryTasks(getGlobalConfiguration(), projectIdentifier, query,
						referenceRegexp, null);
				return FormValidation.ok(Messages.test_query_found_tasks(foundTasks.length));
			} catch (Exception e) {
				return FormValidation.error(Messages.testconnection_error(e.getMessage()));
			}
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