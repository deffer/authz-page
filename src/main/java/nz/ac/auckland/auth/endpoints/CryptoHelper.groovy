package nz.ac.auckland.auth.endpoints

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

import javax.annotation.PostConstruct
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.xml.bind.DatatypeConverter
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException

@Service
class CryptoHelper {
	private static final Logger logger = LoggerFactory.getLogger(CryptoHelper.class);

	@Value('${as.consentTokenSignatureKey}') //Secret Key
	private String key;

	private Mac sha256_HMAC;

	@PostConstruct
	public void init() throws NoSuchAlgorithmException, InvalidKeyException {
		logger.info("Initializing the HMAC.....");
		sha256_HMAC = Mac.getInstance("HmacSHA256");
		SecretKeySpec secretKey = new SecretKeySpec(DatatypeConverter.parseHexBinary(key), sha256_HMAC.getAlgorithm());
		sha256_HMAC.init(secretKey);
	}

	public String sign(String data) throws UnsupportedEncodingException {
		logger.debug("Signing the Message using HMAC_SHA256........ ($data)");
		return DatatypeConverter.printHexBinary(sha256_HMAC.doFinal(data.getBytes("UTF-8")));
	}

}
