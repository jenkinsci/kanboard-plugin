package org.mably.jenkins.plugins.kanboard;

import static com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials;

import java.io.IOException;

import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2Session;

import hudson.Extension;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.interceptor.RequirePOST;

@Extension
public class KanboardGlobalConfiguration extends GlobalConfiguration {

	static final String ENDPOINT_FIELD = "endpoint";
	static final String APITOKEN_FIELD = "apiToken";
	static final String APITOKENCREDENTIALID_FIELD = "apiTokenCredentialId";
	static final String ATTACHMENTMAXSIZE_FIELD = "attachmentMaxSize";
	static final String DEBUGMODE_FIELD = "debugMode";

	static final long DEFAULT_ATTACHMENTMAXSIZE = 1000000;

	public String endpoint;
	public String apiToken;
	public String apiTokenCredentialId;
	public long attachmentMaxSize = DEFAULT_ATTACHMENTMAXSIZE;
	public boolean debugMode;

	/**
	 * In order to load the persisted global configuration, you have to call
	 * load() in the constructor.
	 */
	public KanboardGlobalConfiguration() {
		load();
	}

	/**
	 * @return Kanboard plugin global configuration
	 */
	public static KanboardGlobalConfiguration get() {
		return GlobalConfiguration.all().get(KanboardGlobalConfiguration.class);
	}

	/**
	 * @return Kanboard endpoint URL
	 */
	public String getEndpoint() {
		return endpoint;
	}

	/**
	 * @return Kanboard JSON/RPC API token
	 */
	public String getApiToken() {
		return apiToken;
	}

	public String getApiTokenCredentialId() {
		return apiTokenCredentialId;
	}

	public long getAttachmentMaxSize() {
		return attachmentMaxSize;
	}

	public boolean isDebugMode() {
		return debugMode;
	}

	/**
	 * This human readable name is used in the configuration screen.
	 */
	@Override
	public String getDisplayName() {
		return Messages.kanboard_publisher();
	}

	@RequirePOST
	public FormValidation doCheckEndpoint(@QueryParameter String endpoint) throws IOException, ServletException {
		Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
		if (StringUtils.isNotBlank(endpoint)) {
			if (!Utils.checkJSONRPCEndpoint(endpoint)) {
				return FormValidation.error(Messages.invalid_endpoint_error());
			}
		}
		return FormValidation.ok();
	}

	public FormValidation doCheckApiToken(@QueryParameter String apiToken) throws IOException, ServletException {
		return FormValidation.ok();
	}

	public FormValidation doTestConnection(@QueryParameter(ENDPOINT_FIELD) final String endpoint,
			@QueryParameter(APITOKEN_FIELD) final String apiToken,
			@QueryParameter(APITOKENCREDENTIALID_FIELD) final String apiTokenCredentialId)
			throws IOException, ServletException {
		if (StringUtils.isNotBlank(endpoint) && Utils.checkJSONRPCEndpoint(endpoint)
				&& (StringUtils.isNotBlank(apiToken) || StringUtils.isNotBlank(apiTokenCredentialId))) {
			try {
				JSONRPC2Session session = Utils.initJSONRPCSession(endpoint, apiToken, apiTokenCredentialId);
				String version = Kanboard.getVersion(session, null, false);
				return FormValidation.ok(Messages.testconnection_success(version));
			} catch (Exception e) {
				return FormValidation.error(Messages.testconnection_error(e.getMessage()));
			}
		} else {
			return FormValidation.error(Messages.testconnection_invalid());
		}
	}

	@Override
	public boolean configure(StaplerRequest req, net.sf.json.JSONObject formData) throws FormException {
		endpoint = formData.getString(ENDPOINT_FIELD);
		apiToken = formData.getString(APITOKEN_FIELD);
		apiTokenCredentialId = formData.getString(APITOKENCREDENTIALID_FIELD);
		attachmentMaxSize = formData.getLong(ATTACHMENTMAXSIZE_FIELD);
		debugMode = formData.getBoolean(DEBUGMODE_FIELD);
		save();
		return super.configure(req, formData);
	}

	public ListBoxModel doFillApiTokenCredentialIdItems(@QueryParameter final String endpoint) {
		Jenkins jenkins = Jenkins.getInstance();
		if ((jenkins != null) && !jenkins.hasPermission(Jenkins.ADMINISTER)) {
			return new ListBoxModel();
		}
		return new StandardListBoxModel().withEmptySelection().withAll(lookupCredentials(StringCredentials.class,
				jenkins, ACL.SYSTEM, URIRequirementBuilder.fromUri(endpoint).build()));
	}
}