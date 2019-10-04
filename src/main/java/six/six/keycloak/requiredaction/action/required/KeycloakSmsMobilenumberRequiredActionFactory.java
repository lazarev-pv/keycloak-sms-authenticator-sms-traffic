package six.six.keycloak.requiredaction.action.required;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

/**
 * Mobile Number Input (RequireAction)
 * Created by nickpack on 15/08/2017.
 */
public class KeycloakSmsMobilenumberRequiredActionFactory implements RequiredActionFactory {
    private static final Logger logger = Logger.getLogger(KeycloakSmsMobilenumberRequiredActionFactory.class);
    private static final KeycloakSmsMobilenumberRequiredAction SINGLETON = new KeycloakSmsMobilenumberRequiredAction();

    @Override
    public RequiredActionProvider create(final KeycloakSession session) {
        logger.debug("create called ...");
        return SINGLETON;
    }

    @Override
    public String getId() {
        logger.debug("getId called ... returning " + KeycloakSmsMobilenumberRequiredAction.PROVIDER_ID);
        return KeycloakSmsMobilenumberRequiredAction.PROVIDER_ID;
    }

    @Override
    public String getDisplayText() {
        logger.debug("getDisplayText called ...");
        return "Update Mobile Number";
    }

    @Override
    public void init(final Config.Scope config) {
        logger.debug("init called ...");
    }

    @Override
    public void postInit(final KeycloakSessionFactory factory) {
        logger.debug("postInit called ...");
    }

    @Override
    public void close() {
        logger.debug("getId close ...");
    }
}
