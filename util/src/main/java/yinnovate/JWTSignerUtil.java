package yinnovate;

import yinnovate.JWTSigner;

public class JWTSignerUtil {
    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new Exception("Usage: JWTSignerUtil <userid> <keyring> <alias> <audience> <applicationId>");
        }

        String userid = args[0];
        String keyring = args[1];
        String alias = args[2];
        String audience = args[3];
        String applicationId = args[4];

        String jwt = JWTSigner.generateSignedJWT(userid, keyring, alias, audience, applicationId);

        System.out.println(jwt);
    }
}
