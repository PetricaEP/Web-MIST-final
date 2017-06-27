package ep.db.mendeley;

import java.util.Calendar;
import java.util.Date;

/**
 * Internal version of MendeleySdk.
 * <p>
 * This is used to run integration tests on the SDK, in which sign is handled via the resource owner
 * password flow.
 * <p>
 * Developer applications should not use this class.
 */
public class InMemoryAuthTokenManager implements AuthTokenManager {

    private String accessToken; // null if not set
    private String refreshToken;
    private Date expiresAt;
    private String tokenType;

    @Override
    public synchronized void saveTokens(String accessToken, String refreshToken, String tokenType, int expiresIn) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAt = generateExpiresAtFromExpiresIn(expiresIn);
        this.tokenType = tokenType;
    }

    @Override
    public synchronized void clearTokens() {
        accessToken = null;
    }


    @Override
    public synchronized Date getAuthTokenExpirationDate() {
        return expiresAt;
    }

    @Override
    public synchronized String getTokenType() {
        return tokenType;
    }

    @Override
    public synchronized String getRefreshToken() {
        return refreshToken;
    }

    @Override
    public synchronized String getAccessToken() {
        return accessToken;
    }


    private synchronized Date generateExpiresAtFromExpiresIn(int expiresIn) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.SECOND, expiresIn);
        return c.getTime();
    }

	public synchronized boolean tokenHasExpired() {
		return expiresAt == null || expiresAt.before(Calendar.getInstance().getTime());
	}
}