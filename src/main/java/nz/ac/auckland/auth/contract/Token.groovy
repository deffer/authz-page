package nz.ac.auckland.auth.contract

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import nz.ac.auckland.auth.config.ApplicationContextProvider
import nz.ac.auckland.auth.endpoints.CryptoHelper

import java.util.regex.Matcher
import java.util.regex.Pattern

@JsonIgnoreProperties(ignoreUnknown = true)
class Token {

	// for consent tokens, the format of access_token field is "{salt}hmac{signature}"
	//   where signature is hmac_265 of "{salt}s{expires_in}"
	//   for consents that never expires, expires_in is set to 0 (reflected as "0000" when signature is generated)
	// for consent tokens, expires_in is always set to 0, otherwise kong would delete it after 14 days
	String access_token
	String api_id
	String authenticated_userid
	Long created_at // 1506636019000
	String credential_id
	Long expires_in // 7200, or 2592000, or 0 for never
	String id
	String scope // "identity-read identity-write default"
	String token_type //"bearer"


	@JsonIgnore
	Boolean consentToken = null // null indicates that it wasnt initialized yet (need to call init() )
	@JsonIgnore
	CryptoHelper cryptoHelper

	static final String CONSENT_USER_SUFFIX="_consent"
	static final String CONSENT_NO_EXPIRE_MS="0000"
	static final String TOKEN_TYPE="bearer"

	static def generator = { String alphabet, int n ->
		return new Random().with {
			(1..n).collect { alphabet[ nextInt( alphabet.length() ) ] }.join('')
		}
	}

	public static Token generateConsentToken(Long consentExpiresInS, String userId,
	                                         String scope, String apiId, String credentialsId){
		//long currentTime = System.currentTimeMillis()

		String salt = generator( ('0'..'9').join(''), 12 )

		Token result = new Token(
				//created_at: currentTime, // doesn't work as expected, kong will round it up
				expires_in: consentExpiresInS,
				authenticated_userid: userId+CONSENT_USER_SUFFIX,
				api_id: apiId, credential_id: credentialsId,
				scope: scope, token_type: TOKEN_TYPE
		)
		result.access_token = "${salt}hmac"+result.getSignature(salt)
		return result
	}

	@JsonIgnore
	Pattern consentTokenPattern = ~/^(\d+)hmac(.*)/

	boolean isConsentToken() {
		if (consentToken == null)
			init()
		return consentToken?.booleanValue()
	}

	public void init(){

		if (!authenticated_userid || !access_token || !authenticated_userid?.endsWith(CONSENT_USER_SUFFIX)){
			consentToken = Boolean.FALSE
			return
		}

		Matcher matcher = consentTokenPattern.matcher(access_token)
		if (matcher.matches()){
			def firstMatch = matcher[0]
			String salt = firstMatch[1]
			String signature = firstMatch[2]

			// check if it was generated by us with the current key
			String calculatedSignature = getSignature(salt)
			if (signature == calculatedSignature){
				consentToken = Boolean.TRUE
				return
			}
		}
		consentToken = Boolean.FALSE
	}

	String getSignature(String salt){
		if (!cryptoHelper)
			cryptoHelper = ApplicationContextProvider.getBean(CryptoHelper.class)

		String expiresInStr = expires_in == 0l? CONSENT_NO_EXPIRE_MS : expires_in.toString()
		String signMessage = "${salt}s${expiresInStr}"
		return cryptoHelper.sign(signMessage)
	}

	boolean stillActive(){
		if (expires_in == 0l)
			return true
		long current = System.currentTimeMillis()
		return (created_at + (expires_in*1000)) >= (current + 300000)
	}

	boolean validForAllScopes(List<String> scopesRequired){
		List<String> consentedScopes = scope.trim().replaceAll(",", ' ').split(' ').findAll{!it.trim().isEmpty()} as List<String>
		return consentedScopes.containsAll(scopesRequired)
	}
}
