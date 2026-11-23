package yinnovate;

import com.ibm.crypto.hdwrCCA.provider.IBMJCECCA;
import com.ibm.crypto.ibmjcehybrid.provider.HybridRACFInputStream;

import java.lang.Exception;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Base64;

public class CertPEMListUtil {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new Exception("Usage: yinnovate.CertPEMListUtil <userid> <keyring> <alias>");
        }

        String userid = args[0];
        String keyring = args[1];
        String alias = args[2];

        // Insert HYBRID and IBMJCECCA providers
        Security.insertProviderAt(new com.ibm.crypto.ibmjcehybrid.provider.IBMJCEHYBRID(), 1);
        Security.insertProviderAt(new com.ibm.crypto.hdwrCCA.provider.IBMJCECCA(), 2);

        // Access RACF key ring
        KeyStore ks = KeyStore.getInstance("JCEHYBRIDRACFKS", "IBMJCEHYBRID");

        HybridRACFInputStream hris = new HybridRACFInputStream(userid, keyring, null);

        ks.load(hris, null);

        X509Certificate cert = (X509Certificate) ks.getCertificate(alias);

        String pem = 
            "-----BEGIN CERTIFICATE-----\n" +
            Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(cert.getEncoded()) +
            "\n-----END CERTIFICATE-----";

        System.out.println(pem);
    }
}
