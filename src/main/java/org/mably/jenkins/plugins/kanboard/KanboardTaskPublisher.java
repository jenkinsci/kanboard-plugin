package org.mably.jenkins.plugins.kanboard;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.inject.Inject;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

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
	private String taskTags;
	private String taskComment;
	private String taskSubtaskTitle;

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

	public String getTaskTags() {
		return taskTags;
	}

	public String getTaskComment() {
		return taskComment;
	}

	public String getTaskSubtaskTitle() {
		return taskSubtaskTitle;
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

	@DataBoundSetter
	public void setTaskTags(String taskTags) {
		this.taskTags = taskTags;
	}

	@DataBoundSetter
	public void setTaskComment(String taskComment) {
		this.taskComment = taskComment;
	}

	@DataBoundSetter
	public void setTaskSubtaskTitle(String taskSubtaskTitle) {
		this.taskSubtaskTitle = taskSubtaskTitle;
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

			KanboardGlobalConfiguration config = getDescriptor().getGlobalConfiguration();
			final boolean debugMode = config.isDebugMode();
			final PrintStream logger = listener.getLogger();

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
			String taskCommentValue = TokenMacro.expandAll(build, listener, this.taskComment);
			String taskSubtaskTitleValue = TokenMacro.expandAll(build, listener, this.taskSubtaskTitle);

			String[] taskTagsValue = Utils.getCSVStringValue(build, listener, this.taskTags, 2);

			String[] taskAttachmentsValue = Utils.getCSVStringValue(build, listener, this.taskAttachments);

			String[] taskExternalLinksValue = Utils.getCSVStringValue(build, listener, this.taskExternalLinks);

			logger.println(Messages.kanboard_publisher_running(Utils.getImplementationVersion(), config.getEndpoint(),
					projectIdentifierValue, taskRefValue));

			JSONRPC2Session session = Utils.initJSONRPCSession(config.getEndpoint(), config.getApiToken(),
					config.getApiTokenCredentialId());

			// String version = Kanboard.getVersion(session, null, false);

			JSONObject jsonProject = Kanboard.getProjectByIdentifier(session, logger, projectIdentifierValue,
					debugMode);
			if (jsonProject == null) {
				throw new AbortException(Messages.project_not_found(projectIdentifierValue));
			}
			Object projectId = jsonProject.get(Kanboard.ID);

			JSONArray projectColumns = Kanboard.getProjectColumns(session, logger, projectId, debugMode);

			JSONObject jsonTask = Kanboard.getTaskByReference(session, logger, projectId, taskRefValue, debugMode);

			Object taskId;
			Object ownerId;
			Object columnId;
			Integer colPosition;
			Object swimlaneId;
			String taskURL;
			if (jsonTask == null) {
				taskId = null;
				ownerId = null;
				columnId = null;
				colPosition = 0; // required by newColPosition calculation
				swimlaneId = null;
				taskURL = null;
			} else {
				taskId = jsonTask.get(Kanboard.ID);
				ownerId = jsonTask.get(Kanboard.OWNER_ID);
				columnId = jsonTask.get(Kanboard.COLUMN_ID);
				colPosition = Kanboard.getColPositionFromColumnId(columnId, projectColumns);
				swimlaneId = jsonTask.get(Kanboard.SWIMLANE_ID);
				taskURL = String.valueOf(jsonTask.get(Kanboard.URL));
			}

			boolean columnChanged = false;
			Object newColumnId = null;
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

			Object creatorId = null;
			if (StringUtils.isNotEmpty(taskCreatorValue)) {
				JSONObject jsonCreator = Kanboard.getUserByName(session, logger, taskCreatorValue, debugMode);
				if (jsonCreator != null) {
					creatorId = jsonCreator.get(Kanboard.ID);
				}
			}

			boolean ownerChanged = false;
			Object newOwnerId = null;
			if (StringUtils.isNotEmpty(taskOwnerValue)) {
				if ((creatorId != null) && (taskOwnerValue.equals(taskCreatorValue))) {
					newOwnerId = creatorId;
				} else {
					JSONObject jsonOwner = Kanboard.getUserByName(session, logger, taskOwnerValue, debugMode);
					if (jsonOwner != null) {
						newOwnerId = jsonOwner.get(Kanboard.ID);
					}
				}
				ownerChanged = (newOwnerId != null) && ObjectUtils.notEqual(ownerId, newOwnerId);
			}

			String[] newTaskTags = null;
			boolean tagsChanged = false;
			if (ArrayUtils.isNotEmpty(taskTagsValue)) {

				JSONObject jsonTags;
				if (taskId == null) {
					jsonTags = null;
				} else {
					try {
						jsonTags = Kanboard.getTaskTags(session, logger, taskId, debugMode);
					} catch (Exception e) {
						jsonTags = null;
						logger.println(e.getMessage());
					}
				}

				String[] taskTags = null;
				if (jsonTags != null) {
					taskTags = new String[jsonTags.size()];
					int i = 0;
					for (Object jsonTag : jsonTags.values()) {
						taskTags[i] = String.valueOf(jsonTag);
						i++;
					}
				}

				if (ArrayUtils.isNotEmpty(taskTags)) {
					newTaskTags = (String[]) ArrayUtils.clone(taskTags);
				} else {
					newTaskTags = new String[0];
				}
				for (int i = 0; i < taskTagsValue.length; i++) {
					String tagValue = taskTagsValue[i];
					if (StringUtils.isNotEmpty(tagValue)) {
						boolean removeTag = (tagValue.charAt(0) == '-');
						String tag = tagValue.replaceFirst("[\\+\\-]*([\\w\\-]*)", "$1");
						if (ArrayUtils.contains(newTaskTags, tag)) {
							if (removeTag) {
								newTaskTags = (String[]) ArrayUtils.removeElement(newTaskTags, tag);
							}
						} else {
							if (!removeTag) {
								newTaskTags = (String[]) ArrayUtils.add(newTaskTags, tag);
							}
						}
					}
				}
				tagsChanged = !ArrayUtils.isEquals(taskTags, newTaskTags);
			}

			boolean newTask = (taskId == null);
			if (newTask) {

				String taskTitleValue = TokenMacro.expandAll(build, listener, this.taskTitle);
				String taskDescValue = TokenMacro.expandAll(build, listener, this.taskDescription);

				Object createResult = Kanboard.createTask(session, logger, projectId, taskRefValue, creatorId,
						((newOwnerId == null) ? creatorId : newOwnerId), taskTitleValue, taskDescValue, newColumnId,
						newSwimlaneId, taskColorValue, newTaskTags, debugMode);

				if (createResult.equals(Boolean.FALSE)) {
					throw new AbortException(Messages.task_create_error(taskRefValue));
				} else {
					taskId = String.valueOf(createResult);
				}

				jsonTask = Kanboard.getTask(session, logger, taskId, debugMode);

				if (jsonTask == null) {
					throw new AbortException(Messages.task_fetch_error(taskId));
				} else {
					ownerId = jsonTask.get(Kanboard.OWNER_ID);
					columnId = jsonTask.get(Kanboard.COLUMN_ID);
					colPosition = Kanboard.getColPositionFromColumnId(columnId, projectColumns);
					swimlaneId = jsonTask.get(Kanboard.SWIMLANE_ID);
					taskURL = String.valueOf(jsonTask.get(Kanboard.URL));
				}

			}

			if (!newTask && (ownerChanged || tagsChanged)) {

				Object taskOwnerId = (ownerChanged) ? newOwnerId : ownerId;
				if (Kanboard.updateTask(session, logger, taskId, taskOwnerId, newTaskTags, debugMode)) {
					logger.println(Messages.task_owner_updated(taskRefValue, taskOwnerValue));
				}

			}

			if (!newTask && (columnChanged || swimlaneChanged)) {

				if (Kanboard.moveTaskPosition(session, logger, projectId, taskId,
						(newColumnId == null) ? columnId : newColumnId,
						(newColPosition == null) ? colPosition : newColPosition,
						(newSwimlaneId == null) ? swimlaneId : newSwimlaneId,
						debugMode)) {
					logger.println(Messages.task_position_move(taskRefValue, newColPosition));
				}

			}

			if (ArrayUtils.isNotEmpty(taskAttachmentsValue)) {

				JSONArray jsonFiles = Kanboard.getAllTaskFiles(session, logger, taskId, debugMode);

				Map<String, JSONObject> existingFiles = new HashMap<String, JSONObject>();

				if (jsonFiles != null) {
					for (int i = 0; i < jsonFiles.size(); i++) {
						JSONObject jsonFile = (JSONObject) jsonFiles.get(i);
						String name = String.valueOf(jsonFile.get(Kanboard.NAME));
						existingFiles.put(name, jsonFile);
					}
				}

				for (int i = 0; i < taskAttachmentsValue.length; i++) {

					String path = taskAttachmentsValue[i];

					try {

						File file = new File(path);
						if (!file.exists()) {
							logger.println(Messages.attachment_file_not_found(file.getCanonicalPath()));
							continue;
						}
						if ((config.getAttachmentMaxSize() != 0) && (file.length() > config.getAttachmentMaxSize())) {
							logger.println(Messages.attachment_file_too_big(file.getCanonicalPath(), file.length(),
									config.getAttachmentMaxSize()));
							continue;
						}

						String filename = file.getName();

						if (existingFiles.containsKey(filename)) {

							JSONObject jsonFile = existingFiles.get(filename);
							Object fileId = jsonFile.get(Kanboard.ID);

							if (Kanboard.removeTaskFile(session, logger, fileId, debugMode)) {
								logger.println(Messages.attachment_remove_sucess(filename, taskRefValue));
							}

						}

						String encodedFile = Utils.encodeFileToBase64Binary(file);

						if (Kanboard.createTaskFile(session, logger, projectId,
								taskId, filename, encodedFile, creatorId, debugMode)) {
							logger.println(Messages.attachment_create_sucess(path, taskRefValue));
						}

					} catch (IOException e) {

						logger.println(e.getMessage());

					}
				}

			}

			if (ArrayUtils.isNotEmpty(taskExternalLinksValue)) {

				JSONArray jsonLinks = Kanboard.getAllExternalTaskLinks(session, logger, taskId, debugMode);

				Map<String, JSONObject> existingLinks = new HashMap<String, JSONObject>();

				if (jsonLinks != null) {
					for (int i = 0; i < jsonLinks.size(); i++) {
						JSONObject jsonLink = (JSONObject) jsonLinks.get(i);
						String url = String.valueOf(jsonLink.get(Kanboard.URL));
						existingLinks.put(url, jsonLink);
					}
				}

				for (int i = 0; i < taskExternalLinksValue.length; i++) {

					String[] linkItems = taskExternalLinksValue[i].split(Pattern.quote("|"));

					String url = linkItems[0];

					if (existingLinks.containsKey(url)) {
						continue; // Don't create already existing links
					}

					String title = null;
					String type = null;
					if (linkItems.length >= 2) {
						if (ArrayUtils.contains(Kanboard.LINKTYPES, linkItems[1])) {
							type = linkItems[1];
						}
						if (linkItems.length >= 3) {
							title = linkItems[2];
						}
					}

					try {

						if (Kanboard.createExternalTaskLink(session, logger, taskId, url, title, type,
								creatorId, debugMode)) {
							logger.println(Messages.external_link_create_success(url, taskRefValue));
						}

					} catch (IOException e) {

						logger.println(e.getMessage());

					}
				}

			}

			if (StringUtils.isNotBlank(taskCommentValue)) {

				Object createResult = Kanboard.createComment(session, logger, taskId, creatorId,
						taskCommentValue, debugMode);
				if (!createResult.equals(Boolean.FALSE)) {
					logger.println(Messages.comment_create_sucess(taskCommentValue, createResult, taskRefValue));
				}

			}

			if (StringUtils.isNotBlank(taskSubtaskTitleValue)) {

				Object jsonSubtasksResult = Kanboard.getAllSubtasks(session, logger, taskId, debugMode);

				Map<String, JSONObject> existingSubtasks = new HashMap<String, JSONObject>();

				if (jsonSubtasksResult instanceof JSONArray) {
					JSONArray jsonSubtasks = (JSONArray) jsonSubtasksResult;
					for (int i = 0; i < jsonSubtasks.size(); i++) {
						JSONObject jsonSubtask = (JSONObject) jsonSubtasks.get(i);
						String title = String.valueOf(jsonSubtask.get(Kanboard.TITLE));
						String userId = String.valueOf(jsonSubtask.get(Kanboard.USER_ID));
						String key = title + "|" + userId;
						existingSubtasks.put(key, jsonSubtask);
					}
				}

				String key = taskSubtaskTitleValue + "|" + ownerId;

				if (!existingSubtasks.containsKey(key)) {

					try {

						Object createResult = Kanboard.createSubtask(session, logger, taskId, ownerId,
								taskSubtaskTitleValue, debugMode);
						if (!createResult.equals(Boolean.FALSE)) {
							logger.println(
									Messages.subtask_create_sucess(taskSubtaskTitleValue, createResult, taskRefValue));
						}

					} catch (IOException e) {

						logger.println(e.getMessage());

					}

				}

			}

			// Export task URL environment variable
			if (StringUtils.isNotBlank(taskURL)) {
				Utils.exportEnvironmentVariable(build, KanboardPlugin.KANBOARD_TASKURL_ENVVAR, taskURL);
				logger.println(Messages.taskurl_envvar_success(taskURL, KanboardPlugin.KANBOARD_TASKURL_ENVVAR));
			}

		} catch (JSONRPC2SessionException | IOException | InterruptedException | MacroEvaluationException e) {

			throw new AbortException(e.getMessage());

		}

		return true;
	}

	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

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
			return Messages.kanboard_publisher();
		}

		/**
		 * @return Fills task color selection dropdown list
		 */
		public ListBoxModel doFillTaskColorItems() {
			ListBoxModel items = new ListBoxModel();
			items.add(Messages.defaultColor(), Kanboard.DEEP_ORANGE);
			items.add(Messages.yellow(), Kanboard.YELLOW);
			items.add(Messages.blue(), Kanboard.BLUE);
			items.add(Messages.green(), Kanboard.GREEN);
			items.add(Messages.purple(), Kanboard.PURPLE);
			items.add(Messages.red(), Kanboard.RED);
			items.add(Messages.orange(), Kanboard.ORANGE);
			items.add(Messages.grey(), Kanboard.GREY);
			items.add(Messages.brown(), Kanboard.BROWN);
			items.add(Messages.deepOrange(), Kanboard.DEEP_ORANGE);
			items.add(Messages.darkGrey(), Kanboard.DARK_GREY);
			items.add(Messages.pink(), Kanboard.PINK);
			items.add(Messages.teal(), Kanboard.TEAL);
			items.add(Messages.cyan(), Kanboard.CYAN);
			items.add(Messages.lime(), Kanboard.LIME);
			items.add(Messages.lightGreen(), Kanboard.LIGHT_GREEN);
			items.add(Messages.amber(), Kanboard.AMBER);
			items.add(Messages.colorEnvVar(), "$" + KanboardPlugin.KANBOARD_TASKCOLOR_ENVVAR);
			return items;
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
