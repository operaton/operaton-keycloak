package org.operaton.bpm.extension.keycloak;

import org.operaton.bpm.extension.keycloak.util.ContentType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;

/**
 * Keycloak context holding authorization header / access token.
 */
public class KeycloakContext {

	private HttpHeaders headers;
	
	private long expiresAt;
	
	String refreshToken;
	
	public KeycloakContext(String accessToken, String tokenType, long expiresInMillis, String refreshToken, String charset) {
		headers = new HttpHeaders();
		headers.add(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON + ";charset="+charset);
		headers.add(HttpHeaders.AUTHORIZATION, tokenType + " " + accessToken);
		expiresAt = System.currentTimeMillis() + expiresInMillis - 2000;
		this.refreshToken = refreshToken;
	}
	
	/**
	 * Creates a new HTTP Request entity including the authorization header.
	 * @return the request entity
	 */
	public HttpEntity<String> createHttpRequestEntity() {
		return new HttpEntity<>(headers);
	}
 	
	/**
	 * Whether the access token needs to be refreshed
	 * @return {@code true} in case a refresh is required
	 */
	public boolean needsRefresh() {
		return System.currentTimeMillis() >= expiresAt;
	}

	/**
	 * The refresh token
	 * @return the refreshToken
	 */
	public String getRefreshToken() {
		return refreshToken;
	}

}
