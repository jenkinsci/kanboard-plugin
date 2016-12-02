package org.mably.jenkins.plugins.kanboard;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2Session;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2SessionException;

import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

/**
 * TODO
 *
 * @author Francois Masurel
 */
public class KanboardTaskPublisher extends Notifier {

	private final String projectIdentifier;
	private final String taskReference;

	private boolean successfulBuildOnly;
	private String taskTitle;
	private String taskColumn;
	private String taskOwner;
	private String taskCreator;
	private String taskDescription;
	private String taskAttachments;
	private String taskExternalLinks;
	private String taskSwimlane;
	private String taskColor;

	@DataBoundConstructor
	public KanboardTaskPublisher(String projectIdentifier, String taskReference) {
		this.projectIdentifier = projectIdentifier;
		this.taskReference = taskReference;
	}

	public String getProjectIdentifier() {
		return projectIdentifier;
	}

	public String getTaskReference() {
		return taskReference;
	}

	public boolean isSuccessfulBuildOnly() {
		return successfulBuildOnly;
	}

	public String getTaskTitle() {
		return taskTitle;
	}

	public String getTaskColumn() {
		return taskColumn;
	}

	public String getTaskOwner() {
		return taskOwner;
	}

	public String getTaskCreator() {
		return taskCreator;
	}

	public String getTaskDescription() {
		return taskDescription;
	}

	public String getTaskAttachments() {
		return taskAttachments;
	}

	public String getTaskExternalLinks() {
		return taskExternalLinks;
	}

	public String getTaskSwimlane() {
		return taskSwimlane;
	}

	public String getTaskColor() {
		return taskColor;
	}

	@DataBoundSetter
	public void setSuccessfulBuildOnly(boolean successfulBuildOnly) {
		this.successfulBuildOnly = successfulBuildOnly;
	}

	@DataBoundSetter
	public void setTaskTitle(String taskTitle) {
		this.taskTitle = taskTitle;
	}

	@DataBoundSetter
	public void setTaskColumn(String taskColumn) {
		this.taskColumn = taskColumn;
	}

	@DataBoundSetter
	public void setTaskOwner(String taskOwner) {
		this.taskOwner = taskOwner;
	}

	@DataBoundSetter
	public void setTaskCreator(String taskCreator) {
		this.taskCreator = taskCreator;
	}

	@DataBoundSetter
	public void setTaskDescription(String taskDescription) {
		this.taskDescription = taskDescription;
	}

	@DataBoundSetter
	public void setTaskAttachments(String taskAttachments) {
		this.taskAttachments = taskAttachments;
	}

	@DataBoundSetter
	public void setTaskExternalLinks(String taskExternalLinks) {
		this.taskExternalLinks = taskExternalLinks;
	}

	@DataBoundSetter
	public void setTaskSwimlane(String taskSwimlane) {
		this.taskSwimlane = taskSwimlane;
	}

	@DataBoundSetter
	public void setTaskColor(String taskColor) {
		this.taskColor = taskColor;
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
		if (!this.successfulBuildOnly || Result.SUCCESS.equals(build.getResult())) {
			return this.createOrUpdateTask(build, listener);
		} else {
			return true;
		}
	}

	public boolean createOrUpdateTask(AbstractBuild<?, ?> build, TaskListener listener) throws AbortException {

		try {

			String projectIdentifierValue = TokenMacro.expandAll(build, listener, this.projectIdentifier);
			String taskRefValue = TokenMacro.expandAll(build, listener, this.taskReference);

			if (StringUtils.isBlank(projectIdentifierValue) || StringUtils.isBlank(taskRefValue)) {
				throw new AbortException("Valid project identifier and task reference are required.");
			}

			String taskColumnValue = TokenMacro.expandAll(build, listener, this.taskColumn);
			String taskOwnerValue = TokenMacro.expandAll(build, listener, this.taskOwner);
			String taskCreatorValue = TokenMacro.expandAll(build, listener, this.taskCreator);
			String taskSwimlaneValue = TokenMacro.expandAll(build, listener, this.taskSwimlane);
			String taskColorValue = TokenMacro.expandAll(build, listener, this.taskColor);

			String[] taskAttachmentsValue = Kanboard.getTaskAttachemntsValue(build, listener, this.taskAttachments);

			String[] taskExternalLinksValue = Kanboard.getTaskExternalLinksValue(build, listener,
					this.taskExternalLinks);

			listener.getLogger().println("Running Kanboard Task Publisher on " + getDescriptor().getEndpoint()
					+ " for project " + projectIdentifierValue + " and task " + taskRefValue + "!");

			JSONRPC2Session session = Utils.initJSONRPCSession(getDescriptor().getEndpoint(),
					getDescriptor().getApiToken());

			JSONObject jsonProject = Kanboard.getProjectByIdentifier(session, listener, projectIdentifierValue);
			String projectId = (String) jsonProject.get(Kanboard.ID);

			JSONArray projectColumns = Kanboard.getProjectColumns(session, listener, projectId);

			JSONObject jsonTask = Kanboard.getTaskByReference(session, listener, projectId, taskRefValue);

			String taskId;
			String ownerId;
			String columnId;
			Integer colPosition;
			String swimlaneId;
			if (jsonTask == null) {
				taskId = null;
				ownerId = null;
				columnId = null;
				colPosition = 0; // required by newColPosition calculation
				swimlaneId = null;
			} else {
				taskId = (String) jsonTask.get(Kanboard.ID);
				ownerId = (String) jsonTask.get(Kanboard.OWNER_ID);
				columnId = (String) jsonTask.get(Kanboard.COLUMN_ID);
				colPosition = Kanboard.getColPositionFromColumnId(columnId, projectColumns);
				swimlaneId = (String) jsonTask.get(Kanboard.SWIMLANE_ID);
			}

			boolean columnChanged = false;
			String newColumnId = null;
			Integer newColPosition = null;
			if (StringUtils.isNotEmpty(taskColumnValue) && Utils.isInteger(taskColumnValue)) {

				int columnNum = Integer.parseInt(taskColumnValue);

				if (columnNum != 0) {

					if (taskColumnValue.startsWith("+") || taskColumnValue.startsWith("-")) {
						newColPosition = Math.min(colPosition + columnNum, projectColumns.size());
					} else {
						newColPosition = Math.min(columnNum, projectColumns.size());
					}
					newColPosition = Math.max(newColPosition, 1);

					newColumnId = Kanboard.getColumnIdFromColPosition(newColPosition, projectColumns);

					columnChanged = ObjectUtils.notEqual(columnId, newColumnId);
				}
			}

			boolean swimlaneChanged = false;
			String newSwimlaneId = null;
			if (StringUtils.isNotEmpty(taskSwimlaneValue) && Utils.isInteger(taskSwimlaneValue)) {

				int columnNum = Integer.parseInt(taskSwimlaneValue);

				if (columnNum != 0) {

					// TODO idem columns?

					newSwimlaneId = taskSwimlaneValue;

					swimlaneChanged = ObjectUtils.notEqual(swimlaneId, newSwimlaneId);
				}
			}

			String creatorId = null;
			if (StringUtils.isNotEmpty(taskCreatorValue)) {
				JSONObject jsonCreator = Kanboard.getUserByName(session, listener, taskCreatorValue);
				if (jsonCreator != null) {
					creatorId = (String) jsonCreator.get(Kanboard.ID);
				}
			}

			boolean ownerChanged = false;
			String newOwnerId = null;
			if (StringUtils.isNotEmpty(taskOwnerValue)) {
				if ((creatorId != null) && (taskOwnerValue.equals(taskCreatorValue))) {
					newOwnerId = creatorId;
				} else {
					JSONObject jsonOwner = Kanboard.getUserByName(session, listener, taskOwnerValue);
					if (jsonOwner != null) {
						newOwnerId = (String) jsonOwner.get(Kanboard.ID);
					}
				}
				ownerChanged = (newOwnerId != null) && ObjectUtils.notEqual(ownerId, newOwnerId);
			}

			boolean newTask = (taskId == null);
			if (newTask) {

				String taskTitleValue = TokenMacro.expandAll(build, listener, this.taskTitle);
				String taskDescValue = TokenMacro.expandAll(build, listener, this.taskDescription);

				Object createResult = Kanboard.createTask(session, listener, projectId, taskRefValue, creatorId,
						((newOwnerId == null) ? creatorId : newOwnerId), taskTitleValue, taskDescValue, newColumnId,
						newSwimlaneId, taskColorValue);

				if (createResult.equals(Boolean.FALSE)) {
					throw new AbortException("Couldn't create Kanboard task");
				} else {
					taskId = String.valueOf(createResult);
				}

				jsonTask = Kanboard.getTask(session, listener, taskId);

				if (jsonTask == null) {
					throw new AbortException("Couldn't fetch newly created Kanboard task");
				} else {
					columnId = (String) jsonTask.get(Kanboard.COLUMN_ID);
					colPosition = Kanboard.getColPositionFromColumnId(columnId, projectColumns);
					swimlaneId = (String) jsonTask.get(Kanboard.SWIMLANE_ID);
				}

			}

			if (!newTask && ownerChanged) {

				if (Kanboard.updateTask(session, listener, taskId, newOwnerId)) {
					listener.getLogger()
							.println("Owner of task " + taskRefValue + " successfully set to " + taskOwnerValue + ".");
				}

			}

			if (!newTask && (columnChanged || swimlaneChanged)) {

				if (Kanboard.moveTaskPosition(session, listener, Integer.valueOf(projectId), Integer.valueOf(taskId),
						(newColumnId == null) ? Integer.valueOf(columnId) : Integer.valueOf(newColumnId),
						(newColPosition == null) ? colPosition : newColPosition,
						(newSwimlaneId == null) ? Integer.valueOf(swimlaneId) : Integer.valueOf(newSwimlaneId))) {
					listener.getLogger()
							.println("Task " + taskRefValue + " successfully moved to column " + newColPosition + ".");
				}

			}

			if (ArrayUtils.isNotEmpty(taskAttachmentsValue)) {

				JSONArray jsonFiles = Kanboard.getAllTaskFiles(session, listener, Integer.valueOf(taskId));

				Map<String, JSONObject> existingFiles = new HashMap<String, JSONObject>();

				if (jsonFiles != null) {
					for (int i = 0; i < jsonFiles.size(); i++) {
						JSONObject jsonFile = (JSONObject) jsonFiles.get(i);
						String name = (String) jsonFile.get(Kanboard.NAME);
						existingFiles.put(name, jsonFile);
					}
				}

				for (int i = 0; i < taskAttachmentsValue.length; i++) {

					String path = taskAttachmentsValue[i];

					try {

						File file = new File(path);
						if (!file.exists()) {
							listener.getLogger().println("Couldn't find file " + file.getCanonicalPath());
							continue;
						}

						String filename = file.getName();

						if (existingFiles.containsKey(filename)) {

							JSONObject jsonFile = existingFiles.get(filename);
							String fileId = (String) jsonFile.get(Kanboard.ID);

							if (Kanboard.removeTaskFile(session, listener, Integer.valueOf(fileId))) {
								listener.getLogger().println("Existing file " + filename
										+ " successfully removed from task " + taskRefValue + ".");
							}

						}

						String encodedFile = Utils.encodeFileToBase64Binary(file);

						if (Kanboard.createTaskFile(session, listener, Integer.valueOf(projectId),
								Integer.valueOf(taskId), filename, encodedFile, creatorId)) {
							listener.getLogger()
									.println("File " + path + " successfully added to task " + taskRefValue + ".");
						}

					} catch (IOException e) {

						listener.getLogger().println(e.getMessage());

					}
				}

			}

			if (ArrayUtils.isNotEmpty(taskExternalLinksValue)) {

				JSONArray jsonLinks = Kanboard.getAllExternalTaskLinks(session, listener, Integer.valueOf(taskId));

				Map<String, JSONObject> existingLinks = new HashMap<String, JSONObject>();

				if (jsonLinks != null) {
					for (int i = 0; i < jsonLinks.size(); i++) {
						JSONObject jsonLink = (JSONObject) jsonLinks.get(i);
						String url = (String) jsonLink.get(Kanboard.URL);
						existingLinks.put(url, jsonLink);
					}
				}

				for (int i = 0; i < taskExternalLinksValue.length; i++) {

					String url = taskExternalLinksValue[i];

					if (existingLinks.containsKey(url)) {
						continue; // Don't create already existing links
					}

					try {

						if (Kanboard.createExternalTaskLink(session, listener, Integer.valueOf(taskId), url,
								creatorId)) {
							listener.getLogger()
									.println("Link " + url + " successfully added to task " + taskRefValue + ".");
						}

					} catch (IOException e) {

						listener.getLogger().println(e.getMessage());

					}
				}

			}

		} catch (JSONRPC2SessionException | IOException | InterruptedException | MacroEvaluationException e) {

			listener.getLogger().println(e.getMessage());
			throw new AbortException(e.getMessage());

		}

		return true;
	}

	/**
	 * Descriptor for {@link KanboardTaskPublisher}. Used as a singleton. The
	 * class is marked as public so that it can be accessed from views.
	 *
	 */
	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

		private String endpoint;
		private String apiToken;

		/**
		 * In order to load the persisted global configuration, you have to call
		 * load() in the constructor.
		 */
		public DescriptorImpl() {
			load();
		}

		/**
		 * @return
		 */
		public String getEndpoint() {
			return endpoint;
		}

		/**
		 * @return
		 */
		public String getApiToken() {
			return apiToken;
		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true;
		}

		/**
		 * This human readable name is used in the configuration screen.
		 */
		@Override
		public String getDisplayName() {
			return "Kanboard Publisher";
		}

		public FormValidation doCheckEndpoint(@QueryParameter String endpoint) throws IOException, ServletException {
			if (StringUtils.isNotBlank(endpoint)) {
				if (!Utils.checkJSONRPCEndpoint(endpoint)) {
					return FormValidation.error("Please set a valid Kanboard JSON/RPC endpoint URL.");
				}
			}
			return FormValidation.ok();
		}

		public FormValidation doCheckApiToken(@QueryParameter String apiToken) throws IOException, ServletException {
			return FormValidation.ok();
		}

		public FormValidation doTestConnection(@QueryParameter("endpoint") final String endpoint,
				@QueryParameter("apiToken") final String apiToken) throws IOException, ServletException {
			if (StringUtils.isNotBlank(endpoint) && Utils.checkJSONRPCEndpoint(endpoint)
					&& StringUtils.isNotBlank(apiToken)) {
				try {
					JSONRPC2Session session = Utils.initJSONRPCSession(endpoint, apiToken);
					JSONRPC2Request request = new JSONRPC2Request("getVersion", 0);
					JSONRPC2Response response = session.send(request);
					if (response.indicatesSuccess()) {
						String version = (String) response.getResult();
						return FormValidation.ok("Success, Kanboard version " + version + " detected.");
					} else {
						return FormValidation.error("Error : " + response.getError().getMessage());
					}
				} catch (Exception e) {
					return FormValidation.error("Client error : " + e.getMessage());
				}
			} else {
				return FormValidation.error("Invalid endpoint or API token.");
			}
		}

		@Override
		public boolean configure(StaplerRequest req, net.sf.json.JSONObject formData) throws FormException {
			endpoint = formData.getString("endpoint");
			apiToken = formData.getString("apiToken");
			save();
			return super.configure(req, formData);
		}

		public ListBoxModel doFillTaskColorItems() {
			ListBoxModel items = new ListBoxModel();
			items.add("Default", "");
			items.add("Yellow", "yellow");
			items.add("Blue", "blue");
			items.add("Green", "green");
			items.add("Purple", "purple");
			items.add("Red", "red");
			items.add("Orage", "orange");
			items.add("Grey", "grey");
			items.add("Brown", "brown");
			items.add("Deep orange", "deep_orange");
			items.add("Dark grey", "dark_grey");
			items.add("Pink", "pink");
			items.add("Teal", "teal");
			items.add("Cyan", "cyan");
			items.add("Lime", "lime");
			items.add("Light green", "light_green");
			items.add("Amber", "amber");
			return items;
		}
	}

}
