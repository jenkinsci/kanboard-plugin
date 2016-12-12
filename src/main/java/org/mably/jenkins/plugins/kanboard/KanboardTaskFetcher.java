package org.mably.jenkins.plugins.kanboard;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.thetransactioncompany.jsonrpc2.client.JSONRPC2Session;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2SessionException;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONStyleIdent;

/**
 * TODO
 *
 * @author Francois Masurel
 */
public class KanboardTaskFetcher extends Builder {

	static final String KANBOARD_ID_ENVVAR = "KANBOARD_ID";
	static final String KANBOARD_CREATOR_ENVVAR = "KANBOARD_CREATOR";
	static final String KANBOARD_OWNER_ENVVAR = "KANBOARD_OWNER";
	static final String KANBOARD_FILES_ENVVAR = "KANBOARD_FILES";
	static final String KANBOARD_TITLE_ENVVAR = "KANBOARD_TITLE";
	static final String KANBOARD_TASKJSON_ENVVAR = "KANBOARD_TASKJSON";

	private static final String ATTACHMENTS_DIR = "attachments";
	private static final String KANBOARD_BASEDIR = "kanboard";
	private static final String LINKS_DIR = "links";
	private static final String TASK_JSON_FILENAME = "data.json";

	private final String projectIdentifier;
	private final String taskReference;

	private String taskLinks;
	private String taskAttachments;

	@DataBoundConstructor
	public KanboardTaskFetcher(String projectIdentifier, String taskReference) {
		this.projectIdentifier = projectIdentifier;
		this.taskReference = taskReference;
	}

	public String getProjectIdentifier() {
		return projectIdentifier;
	}

	public String getTaskReference() {
		return taskReference;
	}

	public String getTaskLinks() {
		return taskLinks;
	}

	@DataBoundSetter
	public void setTaskLinks(String taskLinks) {
		this.taskLinks = taskLinks;
	}

	public String getTaskAttachments() {
		return taskAttachments;
	}

	@DataBoundSetter
	public void setTaskAttachments(String taskAttachments) {
		this.taskAttachments = taskAttachments;
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.BUILD;
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
			throws InterruptedException, IOException {
		return this.fetchAttachments(build, listener);
	}

	public boolean fetchAttachments(AbstractBuild<?, ?> build, TaskListener listener) throws AbortException {

		try {

			FilePath workspace = build.getWorkspace();
			if (workspace == null) {
				throw new AbortException(Messages.workspace_not_found());
			}

			KanboardGlobalConfiguration config = getDescriptor().getGlobalConfiguration();
			final boolean debugMode = config.isDebugMode();
			final PrintStream logger = listener.getLogger();

			String projectIdentifierValue = TokenMacro.expandAll(build, listener, this.projectIdentifier);
			String taskRefValue = TokenMacro.expandAll(build, listener, this.taskReference);

			String[] taskLinksValue = Utils.getCSVStringValue(build, listener, this.taskLinks);

			String[] taskAttachmentsValue = Utils.getCSVStringValue(build, listener, this.taskAttachments);

			logger.println(Messages.kanboard_fetcher_running(Utils.getImplementationVersion(), config.getEndpoint(),
					projectIdentifierValue, taskRefValue));

			JSONRPC2Session session = Utils.initJSONRPCSession(config.getEndpoint(), config.getApiToken(),
					config.getApiTokenCredentialId());

			JSONObject jsonProject = Kanboard.getProjectByIdentifier(session, logger, projectIdentifierValue,
					debugMode);
			if (jsonProject == null) {
				throw new AbortException(Messages.project_not_found(projectIdentifierValue));
			}
			String projectId = (String) jsonProject.get(Kanboard.ID);

			JSONObject jsonTask = Kanboard.getTaskByReference(session, logger, projectId, taskRefValue, debugMode);

			if (jsonTask == null) {

				logger.println(Messages.task_not_found(taskRefValue));

			} else {

				String taskId = (String) jsonTask.get(Kanboard.ID);
				String creatorId = (String) jsonTask.get(Kanboard.CREATOR_ID);
				String ownerId = (String) jsonTask.get(Kanboard.OWNER_ID);
				String title = (String) jsonTask.get(Kanboard.TITLE);

				Utils.exportEnvironmentVariable(build, KANBOARD_ID_ENVVAR, taskId);

				String creatorName = null;
				if (StringUtils.isNotBlank(creatorId)) {
					JSONObject jsonCreator = Kanboard.getUser(session, logger, creatorId, debugMode);
					if (jsonCreator != null) {
						creatorName = (String) (String) jsonCreator.get(Kanboard.USERNAME);
						Utils.exportEnvironmentVariable(build, KANBOARD_CREATOR_ENVVAR, creatorName);
					}
				}

				String ownerName = null;
				if (StringUtils.isNotBlank(ownerId)) {
					if (ownerId.equals(creatorId)) {
						ownerName = creatorName;
						Utils.exportEnvironmentVariable(build, KANBOARD_OWNER_ENVVAR, ownerName);
					} else {
						JSONObject jsonOwner = Kanboard.getUser(session, logger, ownerId, debugMode);
						if (jsonOwner != null) {
							ownerName = (String) (String) jsonOwner.get(Kanboard.USERNAME);
							Utils.exportEnvironmentVariable(build, KANBOARD_OWNER_ENVVAR, ownerName);
						}
					}
				}

				if (StringUtils.isNotBlank(title)) {
					Utils.exportEnvironmentVariable(build, KANBOARD_TITLE_ENVVAR, title);
				}

				List<String> fetchedAttachments = new ArrayList<String>();

				if (ArrayUtils.isNotEmpty(taskLinksValue)) {

					JSONArray jsonLinks = Kanboard.getAllExternalTaskLinks(session, logger, Integer.valueOf(taskId),
							debugMode);

					Map<String, JSONObject> existingLinks = new HashMap<String, JSONObject>();

					if (jsonLinks != null) {
						for (int i = 0; i < jsonLinks.size(); i++) {
							JSONObject jsonLink = (JSONObject) jsonLinks.get(i);
							String type = (String) jsonLink.get(Kanboard.LINK_TYPE);
							if (Kanboard.LINKTYPE_ATTACHMENT.equals(type)) {
								String linkUrl = (String) jsonLink.get(Kanboard.URL);
								try {
									URL url = new URL(linkUrl);
									existingLinks.put(FilenameUtils.getName(url.getPath()), jsonLink);
								} catch (MalformedURLException e) {
									continue;
								}
							}
						}
					}

					for (int i = 0; i < taskLinksValue.length; i++) {

						try {

							String linkValue = taskLinksValue[i];

							if (existingLinks.containsKey(linkValue)) {

								JSONObject jsonLink = existingLinks.get(linkValue);
								String linkId = (String) jsonLink.get(Kanboard.ID);
								String linkUrl = (String) jsonLink.get(Kanboard.URL);

								byte[] fetchedData = Utils.fetchURL(new URL(linkUrl));
								if (fetchedData != null) {
									String filepath = workspace
											.child(KANBOARD_BASEDIR + File.separator + taskId + File.separator
													+ LINKS_DIR + File.separator + linkId + File.separator + linkValue)
											.getRemote();
									File file = new File(filepath);
									FileUtils.writeByteArrayToFile(file, fetchedData);
									fetchedAttachments.add(file.getCanonicalPath());
								}

							}

						} catch (IOException e) {

							logger.println(e.getMessage());

						}
					}
				}

				if (ArrayUtils.isNotEmpty(taskAttachmentsValue)) {

					JSONArray jsonFiles = Kanboard.getAllTaskFiles(session, logger, Integer.valueOf(taskId), debugMode);

					Map<String, JSONObject> existingFiles = new HashMap<String, JSONObject>();

					if (jsonFiles != null) {
						for (int i = 0; i < jsonFiles.size(); i++) {
							JSONObject jsonFile = (JSONObject) jsonFiles.get(i);
							String name = (String) jsonFile.get(Kanboard.NAME);
							existingFiles.put(name, jsonFile);
						}
					}

					for (int i = 0; i < taskAttachmentsValue.length; i++) {

						try {

							String filename = taskAttachmentsValue[i];

							if (existingFiles.containsKey(filename)) {

								JSONObject jsonFile = existingFiles.get(filename);
								String fileId = (String) jsonFile.get(Kanboard.ID);

								String base64EncFile = Kanboard.downloadTaskFile(session, logger, fileId, debugMode);
								if (StringUtils.isNotBlank(base64EncFile)) {
									String filepath = workspace.child(KANBOARD_BASEDIR + File.separator + taskId
											+ File.separator + ATTACHMENTS_DIR + File.separator + fileId
											+ File.separator + filename).getRemote();
									File file = Utils.decodeBase64ToBinaryFile(filepath, base64EncFile);
									fetchedAttachments.add(file.getCanonicalPath());
								}

							}

						} catch (IOException e) {

							logger.println(e.getMessage());

						}
					}

				}

				// Export task files environment variable
				String fetchedFilesPathsEnvVar = StringUtils.join(fetchedAttachments, ',');
				if (StringUtils.isNotBlank(fetchedFilesPathsEnvVar)) {

					Utils.exportEnvironmentVariable(build, KANBOARD_FILES_ENVVAR, fetchedFilesPathsEnvVar);
					logger.println(Messages.attachments_envvar_success(fetchedFilesPathsEnvVar, KANBOARD_FILES_ENVVAR));
				}

				jsonTask.put("attachements", fetchedAttachments);

				String taskJSONFilePath = workspace
						.child(KANBOARD_BASEDIR + File.separator + taskId + File.separator + TASK_JSON_FILENAME)
						.getRemote();
				File taskFile = new File(taskJSONFilePath);
				FileUtils.write(taskFile, jsonTask.toJSONString(new JSONStyleIdent()), "UTF-8");

				Utils.exportEnvironmentVariable(build, KANBOARD_TASKJSON_ENVVAR, taskJSONFilePath);
				logger.println(Messages.taskjson_envvar_success(taskJSONFilePath, KANBOARD_TASKJSON_ENVVAR));

			}

		} catch (JSONRPC2SessionException | IOException | InterruptedException | MacroEvaluationException e) {

			throw new AbortException(e.getMessage());

		}

		return true;
	}

	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

		@Inject
		KanboardGlobalConfiguration globalConfiguration;

		public DescriptorImpl() {

		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return Messages.kanboard_attachment_fetcher();
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
