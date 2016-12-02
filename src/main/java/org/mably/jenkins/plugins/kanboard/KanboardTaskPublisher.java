package org.mably.jenkins.plugins.kanboard;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2Session;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2SessionException;

import hudson.AbortException;
import hudson.EnvVars;
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
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

/**
 * TODO
 *
 * @author Francois Masurel
 */
public class KanboardTaskPublisher extends Notifier {

	private boolean successfulBuildOnly;
	private final String projectIdentifier;
	private final String taskReference;
	private final String taskTitle;
	private final String taskColumn;
	private final String taskOwner;
	private final String taskCreator;
	private final String taskDescription;
	private final String taskAttachments;
	private final String taskExternalLinks;

	@DataBoundConstructor
	public KanboardTaskPublisher(boolean successfulBuildOnly, String projectIdentifier, String taskReference,
			String taskTitle, String taskColumn, String taskOwner, String taskCreator, String taskDescription,
			String taskAttachments, String taskExternalLinks) {
		this.successfulBuildOnly = successfulBuildOnly;
		this.projectIdentifier = projectIdentifier;
		this.taskReference = taskReference;
		this.taskTitle = taskTitle;
		this.taskColumn = taskColumn;
		this.taskOwner = taskOwner;
		this.taskCreator = taskCreator;
		this.taskDescription = taskDescription;
		this.taskAttachments = taskAttachments;
		this.taskExternalLinks = taskExternalLinks;
	}

	public boolean isSuccessfulBuildOnly() {
		return successfulBuildOnly;
	}

	public String getProjectIdentifier() {
		return projectIdentifier;
	}

	public String getTaskReference() {
		return taskReference;
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
		if (!this.successfulBuildOnly || build.getResult().equals(Result.SUCCESS)) {
			return this.createOrUpdateTask(build, listener);
		} else {
			return true;
		}
	}

	public boolean createOrUpdateTask(AbstractBuild<?, ?> build, TaskListener listener) throws AbortException {

		try {

			EnvVars environment = build.getEnvironment(listener);

			String projectIdentifierValue = Utils.replaceEnvVars(environment, this.projectIdentifier);
			String taskRefValue = Utils.replaceEnvVars(environment, this.taskReference);

			if (StringUtils.isBlank(projectIdentifierValue) || StringUtils.isBlank(taskRefValue)) {
				throw new AbortException("Valid project identifier and task reference are required.");
			}

			String taskColumnValue = Utils.replaceEnvVars(environment, this.taskColumn);
			String taskOwnerValue = Utils.replaceEnvVars(environment, this.taskOwner);
			String taskCreatorValue = Utils.replaceEnvVars(environment, this.taskCreator);

			String[] taskAttachmentsValue;
			if (StringUtils.isNotBlank(this.taskAttachments)) {
				String[] pathsArray = this.taskAttachments.split(",");
				taskAttachmentsValue = new String[pathsArray.length];
				for (int i = 0; i < pathsArray.length; i++) {
					taskAttachmentsValue[i] = Utils.replaceEnvVars(environment, pathsArray[i]);
				}
			} else {
				taskAttachmentsValue = null;
			}

			String[] taskExternalLinksValue;
			if (StringUtils.isNotBlank(this.taskExternalLinks)) {
				String[] linksArray = this.taskExternalLinks.split(",");
				taskExternalLinksValue = new String[linksArray.length];
				for (int i = 0; i < linksArray.length; i++) {
					taskExternalLinksValue[i] = Utils.replaceEnvVars(environment, linksArray[i]);
				}
			} else {
				taskExternalLinksValue = null;
			}

			listener.getLogger().println("Running Kanboard Task Publisher on " + getDescriptor().getEndpoint()
					+ " for project " + projectIdentifierValue + " and task " + taskRefValue + "!");

			JSONRPC2Session session = Utils.initJSONRPCSession(getDescriptor().getEndpoint(),
					getDescriptor().getApiToken());

			String method;
			int requestID = 0;
			Map<String, Object> params;
			JSONRPC2Request request;
			JSONRPC2Response response;

			// Construct new getProjectByIdentifier request
			method = "getProjectByIdentifier";
			params = new HashMap<String, Object>();
			params.put("identifier", projectIdentifierValue);

			listener.getLogger().println("----------");

			request = new JSONRPC2Request(method, params, requestID);
			listener.getLogger().println(request.toJSONString());

			// Send request
			response = session.send(request);
			listener.getLogger().println(response.toJSONString());
			listener.getLogger().println("----------");

			// Print response result / error
			String projectId;
			if (response.indicatesSuccess()) {
				JSONObject jsonResult = (JSONObject) response.getResult();
				projectId = (String) jsonResult.get("id");
			} else {
				listener.getLogger().println(response.getError().getMessage());
				throw new AbortException(response.getError().getMessage());
			}

			// Construct new getColumns request
			method = "getColumns";
			params = new HashMap<String, Object>();
			params.put("project_id", projectId);

			request = new JSONRPC2Request(method, params, requestID);
			listener.getLogger().println(request.toJSONString());

			// Send request
			response = session.send(request);
			listener.getLogger().println(response.toJSONString());
			listener.getLogger().println("----------");

			// Print response result / error
			JSONArray projectColumns;
			if (response.indicatesSuccess()) {
				if (response.getResult() == null) {
					projectColumns = null;
				} else {
					projectColumns = (JSONArray) response.getResult();
				}
			} else {
				listener.getLogger().println(response.getError().getMessage());
				throw new AbortException(response.getError().getMessage());
			}

			// Construct new getTaskByReference request
			method = "getTaskByReference";
			params = new HashMap<String, Object>();
			params.put("project_id", projectId);
			params.put("reference", taskRefValue);

			request = new JSONRPC2Request(method, params, requestID);
			listener.getLogger().println(request.toJSONString());

			// Send request
			response = session.send(request);
			listener.getLogger().println(response.toJSONString());
			listener.getLogger().println("----------");

			// Print response result / error
			String taskId;
			String columnId;
			if (response.indicatesSuccess()) {
				if (response.getResult() == null) {
					taskId = null;
					columnId = null;
				} else {
					JSONObject jsonResult = (JSONObject) response.getResult();
					taskId = (String) jsonResult.get("id");
					columnId = (String) jsonResult.get("column_id");
				}
			} else {
				listener.getLogger().println(response.getError().getMessage());
				throw new AbortException(response.getError().getMessage());
			}

			String creatorId = null;
			if (StringUtils.isNotEmpty(taskCreatorValue)) {

				// Construct new getUserByName request
				method = "getUserByName";
				params = new HashMap<String, Object>();
				params.put("username", taskCreatorValue);

				request = new JSONRPC2Request(method, params, requestID);
				listener.getLogger().println(request.toJSONString());

				// Send request
				response = session.send(request);
				listener.getLogger().println(response.toJSONString());
				listener.getLogger().println("----------");

				// Print response result / error
				if (response.indicatesSuccess()) {
					if (response.getResult() != null) {
						JSONObject jsonResult = (JSONObject) response.getResult();
						creatorId = (String) jsonResult.get("id");
					}
				} else {
					listener.getLogger().println(response.getError().getMessage());
					throw new AbortException(response.getError().getMessage());
				}
			}

			if (taskId == null) {

				// Construct new createTask request
				method = "createTask";
				params = new HashMap<String, Object>();
				params.put("project_id", projectId);
				params.put("reference", taskRefValue);

				if (creatorId != null) {
					params.put("creator_id", creatorId);
				}

				String taskTitleValue = Utils.replaceEnvVars(environment, this.taskTitle);
				if (taskTitleValue == null) {
					params.put("title", taskRefValue);
				} else {
					params.put("title", taskTitleValue);
				}

				String taskDescValue = Utils.replaceEnvVars(environment, this.taskDescription);
				if (taskDescValue != null) {
					params.put("description", taskDescValue);
				}

				request = new JSONRPC2Request(method, params, requestID);
				listener.getLogger().println(request.toJSONString());

				// Send request
				response = session.send(request);
				listener.getLogger().println(response.toJSONString());
				listener.getLogger().println("----------");

				// Print response result / error
				if (response.indicatesSuccess()) {
					if (response.getResult().equals(Boolean.FALSE)) {
						throw new AbortException("Couldn't create Kanboard task");
					} else {
						taskId = String.valueOf(response.getResult());
					}
				} else {
					listener.getLogger().println(response.getError().getMessage());
					throw new AbortException(response.getError().getMessage());
				}

				// Construct new getTaskByReference request
				method = "getTask";
				params = new HashMap<String, Object>();
				params.put("task_id", Integer.valueOf(taskId));

				request = new JSONRPC2Request(method, params, requestID);
				listener.getLogger().println(request.toJSONString());

				// Send request
				response = session.send(request);
				listener.getLogger().println(response.toJSONString());
				listener.getLogger().println("----------");

				// Print response result / error
				if (response.indicatesSuccess()) {
					if (response.getResult() == null) {
						taskId = null;
						columnId = null;
					} else {
						JSONObject jsonResult = (JSONObject) response.getResult();
						columnId = (String) jsonResult.get("column_id");
					}
				} else {
					listener.getLogger().println(response.getError().getMessage());
					throw new AbortException(response.getError().getMessage());
				}

			}

			String newColumnId = null;
			Integer newPosition = null;
			if (StringUtils.isNotEmpty(taskColumnValue) && Utils.isInteger(taskColumnValue)) {

				int columnNum = Integer.parseInt(taskColumnValue);

				if (columnNum != 0) {

					int currentPosition = 0;
					for (int i = 0; i < projectColumns.size(); i++) {
						JSONObject column = (JSONObject) projectColumns.get(i);
						if (column.get("id").equals(columnId)) {
							currentPosition = Integer.valueOf((String) column.get("position"));
							break;
						}
					}

					if (taskColumnValue.startsWith("+") || taskColumnValue.startsWith("-")) {
						newPosition = Math.min(currentPosition + columnNum, projectColumns.size());
						newPosition = Math.max(newPosition, 1);
					} else {
						newPosition = Math.min(columnNum, projectColumns.size());
					}

					for (int i = 0; i < projectColumns.size(); i++) {
						JSONObject column = (JSONObject) projectColumns.get(i);
						if (column.get("position").equals(String.valueOf(newPosition))) {
							newColumnId = (String) column.get("id");
							break;
						}
					}
				}
			}

			String newOwnerId = null;
			if (StringUtils.isNotEmpty(taskOwnerValue)) {

				if ((creatorId != null) && (taskOwnerValue.equals(taskCreatorValue))) {

					newOwnerId = creatorId;

				} else {

					// Construct new getUserByName request
					method = "getUserByName";
					params = new HashMap<String, Object>();
					params.put("username", taskOwnerValue);

					request = new JSONRPC2Request(method, params, requestID);
					listener.getLogger().println(request.toJSONString());

					// Send request
					response = session.send(request);
					listener.getLogger().println(response.toJSONString());
					listener.getLogger().println("----------");

					// Print response result / error
					if (response.indicatesSuccess()) {
						if (response.getResult() != null) {
							JSONObject jsonResult = (JSONObject) response.getResult();
							newOwnerId = (String) jsonResult.get("id");
						}
					} else {
						listener.getLogger().println(response.getError().getMessage());
						throw new AbortException(response.getError().getMessage());
					}

				}

			}

			if ((newColumnId != null) && (newPosition != null)) {

				// Construct new moveTaskPosition request
				method = "moveTaskPosition";
				params = new HashMap<String, Object>();
				params.put("task_id", Integer.valueOf(taskId));
				params.put("project_id", Integer.valueOf(projectId));

				params.put("column_id", Integer.valueOf(newColumnId));
				params.put("position", Integer.valueOf(newPosition));

				request = new JSONRPC2Request(method, params, requestID);
				listener.getLogger().println(request.toJSONString());

				// Send request
				response = session.send(request);
				listener.getLogger().println(response.toJSONString());
				listener.getLogger().println("----------");

				// Print response result / error
				if (response.indicatesSuccess()) {
					listener.getLogger()
							.println("Task " + taskRefValue + " successfully moved to column " + newPosition + ".");
				} else {
					listener.getLogger().println(response.getError().getMessage());
					throw new AbortException(response.getError().getMessage());
				}

			}

			if (newOwnerId != null) {

				// Construct new updateTask request
				method = "updateTask";
				params = new HashMap<String, Object>();
				params.put("id", taskId);

				if (newOwnerId != null) {
					params.put("owner_id", newOwnerId);
				}

				request = new JSONRPC2Request(method, params, requestID);
				listener.getLogger().println(request.toJSONString());

				// Send request
				response = session.send(request);
				listener.getLogger().println(response.toJSONString());
				listener.getLogger().println("----------");

				// Print response result / error
				if (response.indicatesSuccess()) {
					listener.getLogger()
							.println("Owner of task " + taskRefValue + " successfully set to " + taskOwnerValue + ".");
				} else {
					listener.getLogger().println(response.getError().getMessage());
					throw new AbortException(response.getError().getMessage());
				}

			}

			if (ArrayUtils.isNotEmpty(taskAttachmentsValue)) {

				// Construct new getAllTaskFiles request
				method = "getAllTaskFiles";
				params = new HashMap<String, Object>();
				params.put("task_id", Integer.valueOf(taskId));

				request = new JSONRPC2Request(method, params, requestID);
				listener.getLogger().println(request.toJSONString());

				// Send request
				response = session.send(request);
				listener.getLogger().println(response.toJSONString());
				listener.getLogger().println("----------");

				Map<String, JSONObject> existingFiles = new HashMap<String, JSONObject>();

				// Print response result / error
				if (response.indicatesSuccess()) {
					if (response.getResult() != null) {
						JSONArray jsonResult = (JSONArray) response.getResult();
						for (int i = 0; i < jsonResult.size(); i++) {
							JSONObject jsonFile = (JSONObject) jsonResult.get(i);
							String name = (String) jsonFile.get("name");
							existingFiles.put(name, jsonFile);
						}
					}
				} else {
					listener.getLogger().println(response.getError().getMessage());
					throw new AbortException(response.getError().getMessage());
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
							String fileId = (String) jsonFile.get("id");

							// Construct new removeTaskFile request
							method = "removeTaskFile";
							params = new HashMap<String, Object>();
							params.put("file_id", Integer.valueOf(fileId));

							request = new JSONRPC2Request(method, params, requestID);
							listener.getLogger().println(request.toJSONString());

							// Send request
							response = session.send(request);
							listener.getLogger().println(response.toJSONString());
							listener.getLogger().println("----------");

							// Print response result / error
							if (response.indicatesSuccess()) {
								listener.getLogger().println("Existing file " + filename
										+ " successfully removed from task " + taskRefValue + ".");
							} else {
								listener.getLogger().println(response.getError().getMessage());
								throw new AbortException(response.getError().getMessage());
							}
						}

						String encodedFile = Utils.encodeFileToBase64Binary(file);

						// Construct new createTaskFile request
						method = "createTaskFile";
						params = new HashMap<String, Object>();
						params.put("task_id", Integer.valueOf(taskId));
						params.put("project_id", Integer.valueOf(projectId));

						params.put("filename", filename);
						params.put("blob", encodedFile);

						if (creatorId != null) {
							// params.put("creator_id",
							// Integer.valueOf(creatorId));
						}

						request = new JSONRPC2Request(method, params, requestID);
						listener.getLogger().println(request.toJSONString());

						// Send request
						response = session.send(request);
						listener.getLogger().println(response.toJSONString());
						listener.getLogger().println("----------");

						// Print response result / error
						if (response.indicatesSuccess()) {
							listener.getLogger()
									.println("File " + path + " successfully added to task " + taskRefValue + ".");
						} else {
							listener.getLogger().println(response.getError().getMessage());
							throw new AbortException(response.getError().getMessage());
						}

					} catch (IOException e) {

						listener.getLogger().println(e.getMessage());

					}
				}

			}

			if (ArrayUtils.isNotEmpty(taskExternalLinksValue)) {

				// Construct new getAllExternalTaskLinks request
				method = "getAllExternalTaskLinks";
				params = new HashMap<String, Object>();
				params.put("task_id", Integer.valueOf(taskId));

				request = new JSONRPC2Request(method, params, requestID);
				listener.getLogger().println(request.toJSONString());

				// Send request
				response = session.send(request);
				listener.getLogger().println(response.toJSONString());
				listener.getLogger().println("----------");

				Map<String, JSONObject> existingLinks = new HashMap<String, JSONObject>();

				// Print response result / error
				if (response.indicatesSuccess()) {
					if (response.getResult() != null) {
						JSONArray jsonResult = (JSONArray) response.getResult();
						for (int i = 0; i < jsonResult.size(); i++) {
							JSONObject jsonLink = (JSONObject) jsonResult.get(i);
							String url = (String) jsonLink.get("url");
							existingLinks.put(url, jsonLink);
						}
					}
				} else {
					listener.getLogger().println(response.getError().getMessage());
					throw new AbortException(response.getError().getMessage());
				}

				for (int i = 0; i < taskExternalLinksValue.length; i++) {

					String url = taskExternalLinksValue[i];

					if (existingLinks.containsKey(url)) {
						continue; // Don't create already existing links
					}

					try {

						// Construct new createExternalTaskLink request
						method = "createExternalTaskLink";
						params = new HashMap<String, Object>();
						params.put("task_id", Integer.valueOf(taskId));
						params.put("url", url);
						params.put("dependency", "related");
						params.put("type", "weblink");

						if (creatorId != null) {
							params.put("creator_id", Integer.valueOf(creatorId));
						}

						request = new JSONRPC2Request(method, params, requestID);
						listener.getLogger().println(request.toJSONString());

						// Send request
						response = session.send(request);
						listener.getLogger().println(response.toJSONString());
						listener.getLogger().println("----------");

						// Print response result / error
						if (response.indicatesSuccess()) {
							listener.getLogger()
									.println("Link " + url + " successfully added to task " + taskRefValue + ".");
						} else {
							listener.getLogger().println(response.getError().getMessage());
							throw new AbortException(response.getError().getMessage());
						}

					} catch (IOException e) {

						listener.getLogger().println(e.getMessage());

					}
				}

			}

		} catch (JSONRPC2SessionException | IOException | InterruptedException e) {

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

	}

}
