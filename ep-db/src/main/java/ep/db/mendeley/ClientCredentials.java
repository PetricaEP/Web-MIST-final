package ep.db.mendeley;

import java.util.Base64;

/**
 * <p>
 * Data structure to wrap the client credentials needed to authenticate against the Mendeley
 * Web API.
 * </p> 
 * <p> 
 * To create your application credentials 
 * See dev.mendeley.com
 * </p>
 */
public class ClientCredentials {

    public final String clientId;
    public final String clientSecret;

    /**
     * New client credentials
     * @param clientId client id
     * @param clientSecret client password
     */
    public ClientCredentials(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

	public synchronized String getCredentialsEncoded() {
		return Base64.getEncoder().encodeToString((clientId+":"+clientSecret).trim().getBytes());
	}
}