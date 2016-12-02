package org.mably.jenkins.plugins.kanboard;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;

import com.thetransactioncompany.jsonrpc2.client.ConnectionConfigurator;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2Session;

import hudson.ProxyConfiguration;
import jenkins.model.Jenkins;

public class Utils {

	static final String LOG_SEPARATOR = "----------";

	static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\w+");

	public static final class ApiAuthenticator implements ConnectionConfigurator {

		private String xApiAuthToken;

		public ApiAuthenticator(String apiToken) {
			try {
				byte[] xApiAuthTokenBytes = ("jsonrpc:" + apiToken).getBytes("utf-8");
				this.xApiAuthToken = Base64.encodeBase64String(xApiAuthTokenBytes);
			} catch (UnsupportedEncodingException e) {
				this.xApiAuthToken = "";
				System.out.println(e.getMessage());
			}
		}

		@Override
		public void configure(HttpURLConnection connection) {
			connection.addRequestProperty("X-API-Auth", this.xApiAuthToken);
		}
	}

	public static boolean isInteger(String value) {
		boolean isInteger;
		try {
			Integer.parseInt(value);
			isInteger = true;
		} catch (NumberFormatException e) {
			isInteger = false;
		}
		return isInteger;
	}

	public static Proxy getJenkinsProxy(URL url) {
		Proxy proxy = null;
		Jenkins jenkins = Jenkins.getInstance();
		if (jenkins != null) {
			ProxyConfiguration proxyConfig = jenkins.proxy;
			if ((proxyConfig != null) && (!proxyConfig.name.isEmpty())
					&& !proxyConfig.noProxyHost.contains(url.getHost())) {
				proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyConfig.name, proxyConfig.port));
			}
		}
		return proxy;
	}

	public static JSONRPC2Session initJSONRPCSession(String endpoint, String apiToken) throws MalformedURLException {

		// The JSON-RPC 2.0 server URL
		URL serverURL = new URL(endpoint);

		// Create new JSON-RPC 2.0 client session
		JSONRPC2Session session = new JSONRPC2Session(serverURL);
		session.setConnectionConfigurator(new Utils.ApiAuthenticator(apiToken));

		Proxy proxy = getJenkinsProxy(serverURL);
		if (proxy != null) {
			session.getOptions().setProxy(proxy);
		}

		return session;
	}

	public static boolean checkJSONRPCEndpoint(String endpoint) {
		boolean valid = false;
		try {
			URL endpointURL = new URL(endpoint);
			Proxy proxy = getJenkinsProxy(endpointURL);
			URLConnection conn;
			if (proxy == null) {
				conn = endpointURL.openConnection();
			} else {
				conn = endpointURL.openConnection(proxy);
			}
			conn.connect();
			BufferedReader bufferedReader = new BufferedReader(
					new InputStreamReader(conn.getInputStream(), Charset.defaultCharset()));
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				valid = line.contains("jsonrpc");
				if (valid) {
					break;
				}
			}
			bufferedReader.close();
		} catch (MalformedURLException e) {
			// the URL is not in a valid form
		} catch (IOException e) {
			// the connection couldn't be established
		}
		return valid;
	}

	public static String encodeFileToBase64Binary(File file) throws IOException {
		byte[] bytes = FileUtils.readFileToByteArray(file);
		byte[] encoded = Base64.encodeBase64(bytes);
		return new String(encoded, Charset.defaultCharset());
	}

}
