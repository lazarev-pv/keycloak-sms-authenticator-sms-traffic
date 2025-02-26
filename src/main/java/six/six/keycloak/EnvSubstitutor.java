package six.six.keycloak;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrLookup;
import org.apache.commons.lang.text.StrSubstitutor;
import org.jboss.logging.Logger;
import org.jboss.security.vault.SecurityVaultException;
import org.jboss.security.vault.SecurityVaultUtil;

/***
 * Substitutor based on VAULT, Properties and Environment
 */
public class EnvSubstitutor {

    private static final String VAULT_PREFIX="VAULT::";

    public static final StrSubstitutor envSubstitutor = new StrSubstitutor(new EnvLookUp());
    private static final Logger logger = Logger.getLogger(EnvSubstitutor.class);
    private static class EnvLookUp extends StrLookup {

        @Override
        public String lookup(final String key) {
            String value;
            if(key.contains(VAULT_PREFIX)){
                try {
                    value = SecurityVaultUtil.getValueAsString(key);
                } catch (final SecurityVaultException e) {
                    logger.debug(key + "not present in Vault");
                    value=null;
                }

            }else{
                value =System.getProperty(key);
                if (StringUtils.isBlank(value)) {
                    value = System.getenv(key);
                }
            }

            if (StringUtils.isBlank(value)) {
                throw new IllegalArgumentException("key " + key + " is not found in the env variables");
            }
            return value;
        }
    }
}
