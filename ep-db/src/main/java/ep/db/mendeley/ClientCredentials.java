package ep.db.mendeley;

import org.postgresql.util.Base64;

/**
 * Data structure to wrap the client credentials needed to authenticate against the Mendeley
 * Web API.
 *
 * <p/>
 *
 * To create your application credentials {@see http://dev.mendeley.com/myapps.html}
 */
public class ClientCredentials {

    public final String clientId;
    public final String clientSecret;

    public ClientCredentials(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

	public synchronized String getCredentialsEncoded() {
		return Base64.encodeBytes((clientId+":"+clientSecret).getBytes());
	}
}