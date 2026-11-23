# jwt_signer

Signs a JWT client assertion on z/OS with the private key of a certificate on
a RACF key ring. With it, a batch job or started task can get an OAuth 2.0
access token from Microsoft Entra ID with the client credentials flow,
without a client secret: the private key stays in RACF (or ICSF), and only
the userid that owns the key ring can use it.

## What is in it

| Module | Class | What it does |
|---|---|---|
| `signer` | `yinnovate.JWTSigner` | `generateSignedJWT(userid, keyring, label, audience, applicationId)` returns a signed JWT |
| `util` | `yinnovate.JWTSignerUtil` | Command line: prints the signed JWT on stdout |
| `util` | `yinnovate.CertPEMListUtil` | Command line: prints the certificate (not the key) in PEM, to upload to Entra |

The key ring is read with the IBMJCEHYBRID provider (`JCEHYBRIDRACFKS`), so
the private key may be stored in the RACF database or in ICSF.

## The assertion

| Part | Value |
|---|---|
| Header | `alg` `RS256`, `kid` the certificate label, `x5t#S256` the base64url SHA-256 thumbprint of the certificate |
| `iss`, `sub` | The application (client) ID |
| `aud` | The audience given, for Entra the token endpoint of the tenant |
| `iat`, `exp` | Now, and 5 minutes later |

## Requirements

- z/OS with IBM Semeru Runtime Certified Edition for z/OS, Java 21 or later,
  which has the IBMJCEHYBRID and IBMJCECCA providers.
- A certificate with its private key, connected to a key ring of the userid
  that runs the program.
- READ on FACILITY `IRR.DIGTCERT.LISTRING` for that userid, so it can read
  its own key ring including the private key (or a profile in `RDATALIB`
  that covers the ring, when that class is active).
- To build: Maven, and the artifact
  `com.ibm.semeru-zjavasecurity:ibm.semeru-zjavasecurity:21.0.12.0` in a
  Maven repository you can reach. It is only needed to compile; at run time
  the IBM classes come with the JDK.

## Build

```
mvn package dependency:copy-dependencies -DexcludeArtifactIds=ibm.semeru-zjavasecurity
```

This gives three jars to run with:

```
util/target/yinnovate-jwt-signer-util-1.0.0.jar
util/target/dependency/yinnovate-jwt-signer-1.0.0.jar
util/target/dependency/nimbus-jose-jwt-9.37.jar
```

Leave the IBM security jar out of the class path: the JDK has its own.

## Set up the certificate

For a userid `MYUSER` with key ring and certificate label `MYUSER`, as a
security administrator:

```
RACDCERT ID(MYUSER) ADDRING(MYUSER)
RACDCERT ID(MYUSER) GENCERT SUBJECTSDN(CN('MYUSER')) SIZE(2048) RSA -
         NOTAFTER(DATE(2028-12-31)) WITHLABEL('MYUSER')
RACDCERT ID(MYUSER) CONNECT(ID(MYUSER) LABEL('MYUSER') RING(MYUSER) -
         USAGE(PERSONAL) DEFAULT)
PERMIT IRR.DIGTCERT.LISTRING CLASS(FACILITY) ID(MYUSER) ACCESS(READ)
SETROPTS RACLIST(DIGTCERT DIGTRING FACILITY) REFRESH
```

Add `PKDS` to `GENCERT` to keep the private key in ICSF. Then upload the
certificate to the application registration in Entra, under *Certificates &
secrets*. `CertPEMListUtil` prints it, run as `MYUSER`:

```
java -cp "$JARS/*" yinnovate.CertPEMListUtil MYUSER MYUSER MYUSER > myuser.pem
```

or export it with `RACDCERT ID(MYUSER) EXPORT(LABEL('MYUSER'))
DSN('...') FORMAT(CERTB64)`.

## Use

Run as the userid that owns the key ring, with the three jars in `$JARS`:

```
TENANT=<tenant ID>
CLIENT=<application (client) ID>
TOKENURL=https://login.microsoftonline.com/$TENANT/oauth2/v2.0/token

JWT=$($JAVA_HOME/bin/java -cp "$JARS/*" yinnovate.JWTSignerUtil \
      MYUSER MYUSER MYUSER "$TOKENURL" "$CLIENT")
```

The arguments are the userid that owns the key ring, the key ring, the
certificate label, the audience and the client ID. Then ask Entra for a token
with the assertion:

```
curl -X POST "$TOKENURL" \
  -d "client_id=$CLIENT" \
  -d "scope=<application ID of the API>/.default" \
  -d "grant_type=client_credentials" \
  -d "client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer" \
  -d "client_assertion=$JWT"
```

The answer holds `access_token`. The assertion is valid for 5 minutes: treat
it as a credential for that time, and don't log it. On a shared system,
passing it on a command line makes it visible in the process list; send it
from a file or from the program that reads it instead.

When another program reads the output, for example REXX through `BPXWUNIX`,
add `-Dstdout.encoding=IBM-1047` so Java writes the JWT in EBCDIC.

From Java, call the signer directly:

```java
String jwt = yinnovate.JWTSigner.generateSignedJWT(
    "MYUSER", "MYUSER", "MYUSER", tokenUrl, clientId);
```

## Notes

- Entra's documentation describes `PS256`, `jti` and `nbf` for client
  assertions; Entra accepts the RS256 assertion without `jti` and `nbf`
  made here.
- `AADSTS700027` from Entra means the certificate in the assertion is not
  one of the certificates uploaded to the application.

## License

MIT, see [LICENSE](LICENSE). Nimbus JOSE + JWT and the IBM security
providers it uses have their own licenses.
