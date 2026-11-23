package yinnovate;

import com.ibm.crypto.hdwrCCA.provider.IBMJCECCA;
import com.ibm.crypto.ibmjcehybrid.provider.HybridRACFInputStream;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.lang.Exception;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;

public class JWTSigner {
    public static String generateSignedJWT(String userid, String keyring, String alias, String audience, String applicationId) throws Exception {
        // Insert HYBRID and IBMJCECCA providers
        Security.insertProviderAt(new com.ibm.crypto.ibmjcehybrid.provider.IBMJCEHYBRID(), 1);
        Security.insertProviderAt(new com.ibm.crypto.hdwrCCA.provider.IBMJCECCA(), 2);

        // Access RACF key ring
        KeyStore ks = KeyStore.getInstance("JCEHYBRIDRACFKS", "IBMJCEHYBRID");

        HybridRACFInputStream hris = new HybridRACFInputStream(userid, keyring, null);

        ks.load(hris, null);

        PrivateKey privateKey = (PrivateKey) ks.getKey(alias, null);

        X509Certificate cert = (X509Certificate) ks.getCertificate(alias);

        Base64URL sha256Thumbprint = Base64URL.encode(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));

        RSAPublicKey publicKey = (RSAPublicKey) cert.getPublicKey();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer(applicationId)
            .subject(applicationId)
            .audience(audience)
            .issueTime(new Date())
            .expirationTime(
                new Date(System.currentTimeMillis() + 300_000)
            )
            .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(alias)
            .x509CertSHA256Thumbprint(sha256Thumbprint)
            .build();
        
        SignedJWT jwt = new SignedJWT(header, claims);

        JWSSigner signer = new RSASSASigner(privateKey);

        jwt.sign(signer);

        String token = jwt.serialize();

        return token;
    }
}
