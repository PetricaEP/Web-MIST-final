package ep.db.mendeley;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.grobid.core.utilities.TextUtilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ep.db.utils.Configuration;

/**
 * Class with the implementation of typical request against the /oauth/token endpoint.
 */
public class OAuthTokenEndpoint {

	public final static String TOKENS_URL = "oauth/token";
	public final static String REDIRECT_URI = "http://localhost";

	/**
	 * Base class for every request related to the OAuth process.
	 */
	private static abstract class OAuthTokenRequest {

		protected final AuthTokenManager authTokenManager;
		protected final ClientCredentials clientCredentials;

		public OAuthTokenRequest(AuthTokenManager authTokenManager, ClientCredentials clientCredentials) {
			this.authTokenManager = authTokenManager;
			this.clientCredentials = clientCredentials;
		}

		public void doRun() throws Exception {
			HttpURLConnection conn = null;
			InputStream is = null;
			try {

				URL url = new URL( "https://" + MendeleyConfiguration.getApiBaseUrl() + "/" +
						String.format(TOKENS_URL, getGrantType()));
				
				conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("POST");				
				conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
				conn.setRequestProperty("Authorization", "Basic " + clientCredentials.getCredentialsEncoded());
				

				String postData = "grant_type=" + URLEncoder.encode(getGrantType(), "UTF-8") +  "&scope=all";
				conn.setDoOutput(true);				
				OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream());

			    writer.write(postData);
			    writer.flush();
			    			    				
				final int responseCode = conn.getResponseCode();
				if (responseCode != 200) {					
					String errorMsg = IOUtils.readLines(conn.getErrorStream()).stream().collect(Collectors.joining("\n"));
					throw new Exception(responseCode + " " + conn.getResponseMessage() + " " + url.toString() + "Message: " + errorMsg);
				}

				is = conn.getInputStream();
				String responseBody = TextUtilities.convertStreamToString(is);

				saveTokens(authTokenManager, responseBody);

			} catch (Exception e) {
				throw new Exception("Cannot obtain token", e);
			} finally {
				if ( is != null)
					is.close();
				if (conn != null)
					conn.disconnect();
			}
		}


		public abstract String getGrantType();

		protected abstract void appendOAuthParams(Map<String, String> oauthParams);

		private String getServerDateString(Map<String, List<String>> headersMap) throws IOException {
			final List<String> dateHeaders = headersMap.get("Date");
			if (dateHeaders != null) {
				return headersMap.get("Date").get(0);
			}
			return null;
		}


		private void saveTokens(AuthTokenManager authTokenManager, String serverResponse) throws Exception {
			try {
				ObjectMapper mapper = new ObjectMapper();
				JsonNode jsonResponse = mapper.readTree(serverResponse);
				final String accessToken = jsonResponse.path("access_token").asText();
				final String refreshToken = jsonResponse.path("refresh_token").asText();
				final String tokenType = jsonResponse.path("token_type").asText();
				final int expiresIn = jsonResponse.path("expires_in").asInt();

				authTokenManager.saveTokens(accessToken, refreshToken, tokenType, expiresIn);
				
			} catch (Exception e) {
				throw new Exception("Could not parse the server response with the access token", e);
			}
		}
	}


	/**
	 * Request that obtains an access token. The token will only grant authorization to
	 * the client, but not to perform operations on behalf of any specific user.
	 */
	public static class AccessTokenWithClientCredentialsRequest extends OAuthTokenRequest {

		public AccessTokenWithClientCredentialsRequest(AuthTokenManager authTokenManager, ClientCredentials clientCredentials) {
			super(authTokenManager, clientCredentials);
		}

		@Override
		public String getGrantType() {
			return "client_credentials";
		}

		@Override
		protected void appendOAuthParams(Map<String, String> oauthParams) {
		}
	}

	/**
	 * Request that obtains an access token using one username and password. The token will let perform operations on behalf
	 * of the user.
	 */
	public static class AccessTokenWithPasswordRequest extends OAuthTokenRequest {
		private final String username;
		private final String password;

		public AccessTokenWithPasswordRequest(AuthTokenManager authTokenManager, ClientCredentials clientCredentials, String username, String password) {
			super(authTokenManager, clientCredentials);
			this.username = username;
			this.password = password;
		}

		@Override
		public String getGrantType() {
			return "password";
		}

		@Override
		protected void appendOAuthParams(Map<String, String> oauthParams) {
			oauthParams.put("username", username);
			oauthParams.put("password", password);
		}
	}

	/**
	 * Request that obtains an access token using one authorization token. The token will let perform operations on behalf
	 * of the user.
	 */
	public static class AccessTokenWithAuthorizationCodeRequest extends OAuthTokenRequest {

		private final String authorizationCode;

		public AccessTokenWithAuthorizationCodeRequest(AuthTokenManager authTokenManager, ClientCredentials clientCredentials, String authorizationCode) {
			super(authTokenManager, clientCredentials);
			this.authorizationCode = authorizationCode;
		}

		@Override
		public String getGrantType() {
			return "authorization_code";
		}

		@Override
		protected void appendOAuthParams(Map<String, String> oauthParams) {
			oauthParams.put("redirect_uri", REDIRECT_URI);
			oauthParams.put("code", authorizationCode);
		}
	}

	/**
	 * Request that provides a n updated access token using one existing refresh token.
	 */
	public static class RefreshTokenRequest extends OAuthTokenRequest {

		public RefreshTokenRequest(AuthTokenManager authTokenManager, ClientCredentials clientCredentials) {
			super(authTokenManager, clientCredentials);
		}

		@Override
		public String getGrantType() {
			return "refresh_token";
		}

		@Override
		protected void appendOAuthParams(Map<String, String> oauthParams) {
			oauthParams.put("refresh_token", authTokenManager.getRefreshToken());
		}
	}
	
	public static void main(String[] args) {
		
		Configuration config;
		try {
			config = Configuration.getInstance();
			config.loadConfiguration();
		} catch (IOException e1) {
			e1.printStackTrace();
			return;
		}
		
		MendeleyConfiguration.setApiBaseUrl(config.getMendeleyHost());
		
		AuthTokenManager manager = new InMemoryAuthTokenManager();
		ClientCredentials credentials = new ClientCredentials(config.getMendeleyClientId(), config.getMendeleyClientSecret());
		
		AccessTokenWithClientCredentialsRequest auth = new AccessTokenWithClientCredentialsRequest(manager, credentials);
		try {
			auth.doRun();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		System.out.println(manager.getAccessToken());
		System.out.println(manager.getRefreshToken());
		System.out.println(manager.getAuthTokenExpirationDate());
	}
}