package six.six.gateway.lyrasms;

import org.jboss.logging.Logger;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import six.six.gateway.SMSService;
import six.six.keycloak.KeycloakSmsConstants;

import java.util.Optional;

/**
 * LyraSMS Service implementation
 */
public class LyraSMSService implements SMSService {

    private static final Logger logger = Logger.getLogger(LyraSMSService.class);


    private static final String OPTION = "01100";
    private static final String ACTION = "add";
    private static final String FORWARD = "smsIsSent";
    private static final String DEADLINE = "null";

    private final String url;
    private final LyraSMSRestService remoteService;

    public LyraSMSService(final String url, final Boolean proxyOn) {
        this.url = url;
        this.remoteService = buildClient(url, proxyOn);
    }

    private static LyraSMSRestService buildClient(final String uri, final Boolean proxyOn) {
        final String portTemp = Optional.ofNullable(System.getProperty("http." + KeycloakSmsConstants.PROXY_PORT))
                                        .filter(s -> s != null && !s.isEmpty()).orElse(System.getProperty("https." + KeycloakSmsConstants.PROXY_PORT));

        final String host = Optional.ofNullable(System.getProperty("http." + KeycloakSmsConstants.PROXY_HOST))
                .filter(s -> s != null && !s.isEmpty()).orElse(System.getProperty("https." + KeycloakSmsConstants.PROXY_HOST));
        final int port = portTemp != null ? Integer.valueOf(portTemp) : 8080;
        final String scheme = System.getProperty("http." + KeycloakSmsConstants.PROXY_HOST) != null ? "http" : "https";

        final ResteasyClientBuilder builder = new ResteasyClientBuilder();

        if (proxyOn) {
            builder.defaultProxy(host, port, scheme);
        }

        final ResteasyClient client = builder.disableTrustManager().build();
        final ResteasyWebTarget target = client.target(uri);

        return target
                .proxyBuilder(LyraSMSRestService.class)
                .classloader(LyraSMSRestService.class.getClassLoader())
                .build();

    }

    @Override
    public boolean send(String phoneNumber, final String message, final String login, final String pw) {
        final boolean result;
        if (phoneNumber != null) {
            //Support only this format 3367...
            phoneNumber = phoneNumber.replace("+", "");
        }

        final String resultM = this.remoteService.send(login, pw, phoneNumber, message, OPTION,DEADLINE,null,ACTION,FORWARD,null,null);
        result = resultM.indexOf("status=0") > -1;

        if (!result) {
            logger.error("Fail to send SMS by LyraSMS: " + resultM );
        }
        return result;
    }
}
