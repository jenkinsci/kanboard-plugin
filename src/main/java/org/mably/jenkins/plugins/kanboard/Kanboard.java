package org.mably.jenkins.plugins.kanboard;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2Session;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2SessionException;

import hudson.AbortException;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

public class Kanboard {

	private static final String BLOB = "blob";
	private static final String COLOR_ID = "color_id";
	private static final String CREATOR_ID = "creator_id";
	private static final String DEPENDENCY = "dependency";
	private static final String DEPENDENCY_RELATED = "related";
	private static final String DESCRIPTION = "description";
	private static final String FILE_ID = "file_id";
	private static final String FILENAME = "filename";
	private static final String IDENTIFIER = "identifier";
	private static final String PROJECT_ID = "project_id";
	private static final String REFERENCE = "reference";
	private static final String TASK_ID = "task_id";
	private static final String TYPE = "type";
	private static final String TYPE_WEBLINK = "weblink";
	private static final String USERNAME = "username";

	private static final String CREATE_COMMENT = "createComment";
	private static final String CREATE_EXTERNAL_TASK_LINK = "createExternalTaskLink";
	private static final String CREATE_SUBTASK = "createSubtask";
	private static final String CREATE_TASK = "createTask";
	private static final String CREATE_TASK_FILE = "createTaskFile";
	private static final String GET_ALL_EXTERNAL_TASK_LINKS = "getAllExternalTaskLinks";
	private static final String GET_ALL_SUBTASKS = "getAllSubtasks";
	private static final String GET_ALL_TASK_FILES = "getAllTaskFiles";
	private static final String GET_COLUMNS = "getColumns";
	private static final String GET_PROJECT_BY_IDENTIFIER = "getProjectByIdentifier";
	private static final String GET_TASK = "getTask";
	private static final String GET_TASK_BY_REFERENCE = "getTaskByReference";
	private static final String GET_USER_BY_NAME = "getUserByName";
	private static final String MOVE_TASK_POSITION = "moveTaskPosition";
	private static final String REMOVE_TASK_FILE = "removeTaskFile";
	private static final String UPDATE_TASK = "updateTask";

	static final String COLUMN_ID = "column_id";
	static final String CONTENT = "content";
	static final String ID = "id";
	static final String NAME = "name";
	static final String OWNER_ID = "owner_id";
	static final String POSITION = "position";
	static final String SWIMLANE_ID = "swimlane_id";
	static final String TITLE = "title";
	static final String URL = "url";
	static final String USER_ID = "user_id";

	public static Object createComment(JSONRPC2Session session, TaskListener listener, Integer taskId, String userId,
			String content, boolean debugMode) throws JSONRPC2SessionException, AbortException {

		// Construct new createComment request
		String method = CREATE_COMMENT;
		HashMap<String, Object> params = new HashMap<String, Object>();
		params.put(TASK_ID, taskId);
		params.put(CONTENT, content);

		if (StringUtils.isNotBlank(userId)) {
			params.put(USER_ID, Integer.valueOf(userId));
		}

		JSONRPC2Request request = new JSONRPC2Request(method, params, 0);
		if (debugMode) {
			listener.getLogger().println(request.toJSONString());
		}

		// Send request
		JSONRPC2Response response = session.send(request);
		if (debugMode) {
			listener.getLogger().println(response.toJSONString());
			listener.getLogger().println(Utils.LOG_SEPARATOR);
		}

		// Print response result / error
		if (response.indicatesSuccess()) {
			return response.getResult();
		} else {
			listener.getLogger().println(response.getError().getMessage());
			throw new AbortException(response.getError().getMessage());
		}
	}

	public static boolean createExternalTaskLink(JSONRPC2Session session, TaskListener listener, Integer taskId,
			String url, String creatorId, boolean debugMode) throws JSONRPC2SessionException, AbortException {

		// Construct new createExternalTaskLink request
		String method = CREATE_EXTERNAL_TASK_LINK;
		HashMap<String, Object> params = new HashMap<String, Object>();
		params.put(TASK_ID, taskId);
		params.put(URL, url);
		params.put(DEPENDENCY, DEPENDENCY_RELATED);
		params.put(TYPE, TYPE_WEBLINK);

		if (StringUtils.isNotBlank(creatorId)) {
			params.put(CREATOR_ID, Integer.valueOf(creatorId));
		}

		JSONRPC2Request request = new JSONRPC2Request(method, params, 0);
		if (debugMode) {
			listener.getLogger().println(request.toJSONString());
		}

		// Send request
		JSONRPC2Response response = session.send(request);
		if (debugMode) {
			listener.getLogger().println(response.toJSONString());
			listener.getLogger().println(Utils.LOG_SEPARATOR);
		}

		// Print response result / error
		if (response.indicatesSuccess()) {
			return true;
		} else {
			listener.getLogger().println(response.getError().getMessage());
			throw new AbortException(response.getError().getMessage());
		}
	}

	public static Object createSubtask(JSONRPC2Session session, TaskListener listener, Integer taskId, String userId,
			String title, boolean debugMode) throws JSONRPC2SessionException, AbortException {

		// Construct new createSubtask request
		String method = CREATE_SUBTASK;
		HashMap<String, Object> params = new HashMap<String, Object>();
		params.put(TASK_ID, taskId);
		params.put(TITLE, title);

		if (StringUtils.isNotBlank(userId)) {
			params.put(USER_ID, Integer.valueOf(userId));
		}

		JSONRPC2Request request = new JSONRPC2Request(method, params, 0);
		if (debugMode) {
			listener.getLogger().println(request.toJSONString());
		}

		// Send request
		JSONRPC2Response response = session.send(request);
		if (debugMode) {
			listener.getLogger().println(response.toJSONString());
			listener.getLogger().println(Utils.LOG_SEPARATOR);
		}

		// Print response result / error
		if (response.indicatesSuccess()) {
			return response.getResult();
		} else {
			listener.getLogger().println(response.getError().getMessage());
			throw new AbortException(response.getError().getMessage());
		}
	}

	public static Object createTask(JSONRPC2Session session, TaskListener listener, String projectId,
			String taskRefValue, String creatorId, String ownerId, String taskTitleValue, String taskDescValue,
			String columnId, String swimlaneId, String taskColorValue, boolean debugMode)
			throws JSONRPC2SessionException, AbortException {

		// Construct new createTask request
		String method = CREATE_TASK;
		HashMap<String, Object> params = new HashMap<String, Object>();
		params.put(PROJECT_ID, projectId);
		params.put(REFERENCE, taskRefValue);

		if (StringUtils.isNotBlank(creatorId)) {
			params.put(CREATOR_ID, creatorId);
		}

		if (StringUtils.isNotBlank(ownerId)) {
			params.put(OWNER_ID, ownerId);
		}

		if (StringUtils.isBlank(taskTitleValue)) {
			params.put(TITLE, taskRefValue);
		} else {
			params.put(TITLE, taskTitleValue);
		}

		if (StringUtils.isNotBlank(taskDescValue)) {
			params.put(DESCRIPTION, taskDescValue);
		}

		if (StringUtils.isNotBlank(columnId)) {
			params.put(COLUMN_ID, columnId);
		}

		if (StringUtils.isNotBlank(swimlaneId)) {
			params.put(SWIMLANE_ID, swimlaneId);
		}

		if (StringUtils.isNotBlank(taskColorValue)) {
			params.put(COLOR_ID, taskColorValue);
		}

		JSONRPC2Request request = new JSONRPC2Request(method, params, 0);
		if (debugMode) {
			listener.getLogger().println(request.toJSONString());
		}

		// Send request
		JSONRPC2Response response = session.send(request);
		if (debugMode) {
			listener.getLogger().println(response.toJSONString());
			listener.getLogger().println(Utils.LOG_SEPARATOR);
		}

		// Print response result / error
		if (response.indicatesSuccess()) {
			return response.getResult();
		} else {
			listener.getLogger().println(response.getError().getMessage());
			throw new AbortException(response.getError().getMessage());
		}
	}

	public static boolean createTaskFile(JSONRPC2Session session, TaskListener listener, Integer projectId,
			Integer taskId, String filename, String encodedFile, String creatorId, boolean debugMode)
			throws JSONRPC2SessionException, AbortException {

		// Construct new createTaskFile request
		String method = CREATE_TASK_FILE;
		HashMap<String, Object> params = new HashMap<String, Object>();
		params.put(PROJECT_ID, projectId);
		params.put(TASK_ID, taskId);

		params.put(FILENAME, filename);
		params.put(BLOB, encodedFile);

		JSONRPC2Request request = new JSONRPC2Request(method, params, 0);
		if (debugMode) {
			listener.getLogger().println(request.toJSONString());
		}

		// Send request
		JSONRPC2Response response = session.send(request);
		if (debugMode) {
			listener.getLogger().println(response.toJSONString());
			listener.getLogger().println(Utils.LOG_SEPARATOR);
		}

		// Print response result / error
		if (response.indicatesSuccess()) {
			return true;
		} else {
			listener.getLogger().println(response.getError().getMessage());
			throw new AbortException(response.getError().getMessage());
		}
	}

	public static JSONArray getAllExternalTaskLinks(JSONRPC2Session session, TaskListener listener, Integer taskId,
			boolean debugMode) throws AbortException, JSONRPC2SessionException {

		// Construct new getAllExternalTaskLinks request
		String method = GET_ALL_EXTERNAL_TASK_LINKS;
		HashMap<String, Object> params = new HashMap<String, Object>();
		params.put(TASK_ID, taskId);

		JSONRPC2Request request = new JSONRPC2Request(method, params, 0);
		if (debugMode) {
			listener.getLogger().println(request.toJSONString());
		}

		// Send request
		JSONRPC2Response response = session.send(request);
		if (debugMode) {
			listener.getLogger().println(response.toJSONString());
			listener.getLogger().println(Utils.LOG_SEPARATOR);
		}

		// Print response result / error
		if (response.indicatesSuccess()) {
			return (JSONArray) response.getResult();
		} else {
			listener.getLogger().println(response.getError().getMessage());
			throw new AbortException(response.getError().getMessage());
		}
	}

	public static Object getAllSubtasks(JSONRPC2Session session, TaskListener listener, Integer taskId,
			boolean debugMode) throws AbortException, JSONRPC2SessionException {

		// Construct new getAllSubtasks request
		String method = GET_ALL_SUBTASKS;
		HashMap<String, Object> params = new HashMap<String, Object>();
		params.put(TASK_ID, taskId);

		JSONRPC2Request request = new JSONRPC2Request(method, params, 0);
		if (debugMode) {
			listener.getLogger().println(request.toJSONString());
		}

		// Send request
		JSONRPC2Response response = session.send(request);
		if (debugMode) {
			listener.getLogger().println(response.toJSONString());
			listener.getLogger().println(Utils.LOG_SEPARATOR);
		}

		// Print response result / error
		if (response.indicatesSuccess()) {
			return response.getResult();
		} else {
			listener.getLogger().println(response.getError().getMessage());
			throw new AbortException(response.getError().getMessage());
		}
	}

	public static JSONArray getAllTaskFiles(JSONRPC2Session session, TaskListener listener, Integer taskId,
			boolean debugMode) throws AbortException, JSONRPC2SessionException {

		// Construct new getAllTaskFiles request
		String method = GET_ALL_TASK_FILES;
		HashMap<String, Object> params = new HashMap<String, Object>();
		params.put(TASK_ID, taskId);

		JSONRPC2Request request = new JSONRPC2Request(method, params, 0);
		if (debugMode) {
			listener.getLogger().println(request.toJSONString());
		}

		// Send request
		JSONRPC2Response response = session.send(request);
		if (debugMode) {
			listener.getLogger().println(response.toJSONString());
			listener.getLogger().println(Utils.LOG_SEPARATOR);
		}

		// Print response result / error
		if (response.indicatesSuccess()) {
			return (JSONArray) response.getResult();
		} else {
			listener.getLogger().println(response.getError().getMessage());
			throw new AbortException(response.getError().getMessage());
		}
	}

	public static JSONObject getProjectByIdentifier(JSONRPC2Session session, TaskListener listener,
			String projectIdentifierValue, boolean debugMode) throws JSONRPC2SessionException, AbortException {
		// Construct new getProjectByIdentifier request
		String method = GET_PROJECT_BY_IDENTIFIER;
		Map<String, Object> params = new HashMap<String, Object>();
		params.put(IDENTIFIER, projectIdentifierValue);

		listener.getLogger().println(Utils.LOG_SEPARATOR);

		JSONRPC2Request request = new JSONRPC2Request(method, params, 0);
		if (debugMode) {
			listener.getLogger().println(request.toJSONString());
		}

		// Send request
		JSONRPC2Response response = session.send(request);
		if (debugMode) {
			listener.getLogger().println(response.toJSONString());
			listener.getLogger().println(Utils.LOG_SEPARATOR);
		}

		// Print response result / error
		if (response.indicatesSuccess()) {
			return (JSONObject) response.getResult();
		} else {
			listener.getLogger().println(response.getError().getMessage());
			throw new AbortException(response.getError().getMessage());
		}
	}

	public static JSONArray getProjectColumns(JSONRPC2Session session, TaskListener listener, String projectId,
			boolean debugMode) throws JSONRPC2SessionException, AbortException {

		// Construct new getColumns request
		String method = GET_COLUMNS;
		HashMap<String, Object> params = new HashMap<String, Object>();
		params.put(PROJECT_ID, projectId);

		JSONRPC2Request request = new JSONRPC2Request(method, params, 0);
		if (debugMode) {
			listener.getLogger().println(request.toJSONString());
		}

		// Send request
		JSONRPC2Response response = session.send(request);
		if (debugMode) {
			listener.getLogger().println(response.toJSONString());
			listener.getLogger().println(Utils.LOG_SEPARATOR);
		}

		// Print response result / error
		if (response.indicatesSuccess()) {
			JSONArray projectColumns;
			if (response.getResult() == null) {
				projectColumns = null;
			} else {
				projectColumns = (JSONArray) response.getResult();
			}
			return projectColumns;
		} else {
			listener.getLogger().println(response.getError().getMessage());
			throw new AbortException(response.getError().getMessage());
		}
	}

	public static JSONObject getTask(JSONRPC2Session session, TaskListener listener, String taskId, boolean debugMode)
			throws AbortException, JSONRPC2SessionException {

		// Construct new getTask request
		String method = GET_TASK;
		HashMap<String, Object> params = new HashMap<String, Object>();
		params.put(TASK_ID, Integer.valueOf(taskId));

		JSONRPC2Request request = new JSONRPC2Request(method, params, 0);
		if (debugMode) {
			listener.getLogger().println(request.toJSONString());
		}

		// Send request
		JSONRPC2Response response = session.send(request);
		if (debugMode) {
			listener.getLogger().println(response.toJSONString());
			listener.getLogger().println(Utils.LOG_SEPARATOR);
		}

		// Print response result / error
		if (response.indicatesSuccess()) {
			return (JSONObject) response.getResult();
		} else {
			listener.getLogger().println(response.getError().getMessage());
			throw new AbortException(response.getError().getMessage());
		}
	}

	public static String[] getTaskAttachemntsValue(AbstractBuild<?, ?> build, TaskListener listener,
			String taskAttachments) throws MacroEvaluationException, IOException, InterruptedException {
		String[] taskAttachmentsValue;
		if (StringUtils.isNotBlank(taskAttachments)) {
			String[] pathsArray = taskAttachments.split(",");
			taskAttachmentsValue = new String[pathsArray.length];
			for (int i = 0; i < pathsArray.length; i++) {
				taskAttachmentsValue[i] = TokenMacro.expandAll(build, listener, pathsArray[i]);
			}
		} else {
			taskAttachmentsValue = null;
		}
		return taskAttachmentsValue;
	}

	public static JSONObject getTaskByReference(JSONRPC2Session session, TaskListener listener, String projectId,
			String taskRefValue, boolean debugMode) throws JSONRPC2SessionException, AbortException {

		// Construct new getTaskByReference request
		String method = GET_TASK_BY_REFERENCE;
		HashMap<String, Object> params = new HashMap<String, Object>();
		params.put(PROJECT_ID, projectId);
		params.put(REFERENCE, taskRefValue);

		JSONRPC2Request request = new JSONRPC2Request(method, params, 0);
		if (debugMode) {
			listener.getLogger().println(request.toJSONString());
		}

		// Send request
		JSONRPC2Response response = session.send(request);
		if (debugMode) {
			listener.getLogger().println(response.toJSONString());
			listener.getLogger().println(Utils.LOG_SEPARATOR);
		}

		// Print response result / error
		if (response.indicatesSuccess()) {
			return (JSONObject) response.getResult();
		} else {
			listener.getLogger().println(response.getError().getMessage());
			throw new AbortException(response.getError().getMessage());
		}
	}

	public static String[] getTaskExternalLinksValue(AbstractBuild<?, ?> build, TaskListener listener,
			String taskExternalLinks) throws MacroEvaluationException, IOException, InterruptedException {
		String[] taskExternalLinksValue;
		if (StringUtils.isNotBlank(taskExternalLinks)) {
			String[] linksArray = taskExternalLinks.split(",");
			taskExternalLinksValue = new String[linksArray.length];
			for (int i = 0; i < linksArray.length; i++) {
				taskExternalLinksValue[i] = TokenMacro.expandAll(build, listener, linksArray[i]);
			}
		} else {
			taskExternalLinksValue = null;
		}
		return taskExternalLinksValue;
	}

	public static JSONObject getUserByName(JSONRPC2Session session, TaskListener listener, String username,
			boolean debugMode) throws JSONRPC2SessionException, AbortException {

		// Construct new getUserByName request
		String method = GET_USER_BY_NAME;
		HashMap<String, Object> params = new HashMap<String, Object>();
		params.put(USERNAME, username);

		JSONRPC2Request request = new JSONRPC2Request(method, params, 0);
		if (debugMode) {
			listener.getLogger().println(request.toJSONString());
		}

		// Send request
		JSONRPC2Response response = session.send(request);
		if (debugMode) {
			listener.getLogger().println(response.toJSONString());
			listener.getLogger().println(Utils.LOG_SEPARATOR);
		}

		// Print response result / error
		if (response.indicatesSuccess()) {
			return (JSONObject) response.getResult();
		} else {
			listener.getLogger().println(response.getError().getMessage());
			throw new AbortException(response.getError().getMessage());
		}
	}

	public static boolean moveTaskPosition(JSONRPC2Session session, TaskListener listener, Integer projectId,
			Integer taskId, Integer newColumnId, Integer newPosition, Integer swimlaneId, boolean debugMode)
			throws AbortException, JSONRPC2SessionException {

		// Construct new moveTaskPosition request
		String method = MOVE_TASK_POSITION;
		HashMap<String, Object> params = new HashMap<String, Object>();
		params.put(PROJECT_ID, projectId);
		params.put(TASK_ID, taskId);
		params.put(COLUMN_ID, newColumnId);
		params.put(POSITION, newPosition);
		if (swimlaneId != null) {
			params.put(SWIMLANE_ID, swimlaneId);
		}

		JSONRPC2Request request = new JSONRPC2Request(method, params, 0);
		if (debugMode) {
			listener.getLogger().println(request.toJSONString());
		}

		// Send request
		JSONRPC2Response response = session.send(request);
		if (debugMode) {
			listener.getLogger().println(response.toJSONString());
			listener.getLogger().println(Utils.LOG_SEPARATOR);
		}

		// Print response result / error
		if (response.indicatesSuccess()) {
			return true;
		} else {
			listener.getLogger().println(response.getError().getMessage());
			throw new AbortException(response.getError().getMessage());
		}
	}

	public static boolean removeTaskFile(JSONRPC2Session session, TaskListener listener, Integer fileId,
			boolean debugMode) throws JSONRPC2SessionException, AbortException {

		// Construct new removeTaskFile request
		String method = REMOVE_TASK_FILE;
		HashMap<String, Object> params = new HashMap<String, Object>();
		params.put(FILE_ID, fileId);

		JSONRPC2Request request = new JSONRPC2Request(method, params, 0);
		if (debugMode) {
			listener.getLogger().println(request.toJSONString());
		}

		// Send request
		JSONRPC2Response response = session.send(request);
		if (debugMode) {
			listener.getLogger().println(response.toJSONString());
			listener.getLogger().println(Utils.LOG_SEPARATOR);
		}

		// Print response result / error
		if (response.indicatesSuccess()) {
			return true;
		} else {
			listener.getLogger().println(response.getError().getMessage());
			throw new AbortException(response.getError().getMessage());
		}
	}

	public static boolean updateTask(JSONRPC2Session session, TaskListener listener, String taskId, String newOwnerId,
			boolean debugMode) throws JSONRPC2SessionException, AbortException {

		// Construct new updateTask request
		String method = UPDATE_TASK;
		HashMap<String, Object> params = new HashMap<String, Object>();
		params.put(ID, taskId);

		if (StringUtils.isNotBlank(newOwnerId)) {
			params.put(OWNER_ID, newOwnerId);
		}

		JSONRPC2Request request = new JSONRPC2Request(method, params, 0);
		if (debugMode) {
			listener.getLogger().println(request.toJSONString());
		}

		// Send request
		JSONRPC2Response response = session.send(request);
		if (debugMode) {
			listener.getLogger().println(response.toJSONString());
			listener.getLogger().println(Utils.LOG_SEPARATOR);
		}

		// Print response result / error
		if (response.indicatesSuccess()) {
			return true;
		} else {
			listener.getLogger().println(response.getError().getMessage());
			throw new AbortException(response.getError().getMessage());
		}
	}

	public static int getColPositionFromColumnId(String columnId, JSONArray projectColumns) {
		int position = 0;
		for (int i = 0; i < projectColumns.size(); i++) {
			JSONObject column = (JSONObject) projectColumns.get(i);
			if (column.get(ID).equals(columnId)) {
				position = Integer.parseInt((String) column.get(POSITION));
				break;
			}
		}
		return position;
	}

	public static String getColumnIdFromColPosition(Integer colPosition, JSONArray projectColumns) {
		String columnId = null;
		for (int i = 0; i < projectColumns.size(); i++) {
			JSONObject column = (JSONObject) projectColumns.get(i);
			if (column.get(Kanboard.POSITION).equals(String.valueOf(colPosition))) {
				columnId = (String) column.get(Kanboard.ID);
				break;
			}
		}
		return columnId;
	}

}
