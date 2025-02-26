package six.six.keycloak.authenticator.credential;

import org.keycloak.common.util.Time;
import org.keycloak.credential.*;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.cache.CachedUserModel;
import org.keycloak.models.cache.OnUserCache;
import six.six.keycloak.KeycloakSmsConstants;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by nickpack on 09/08/2017.
 */
public class KeycloakSmsAuthenticatorCredentialProvider implements CredentialProvider, CredentialInputValidator, CredentialInputUpdater, OnUserCache {
    private static final String CACHE_KEY = KeycloakSmsAuthenticatorCredentialProvider.class.getName() + "." + KeycloakSmsConstants.USR_CRED_MDL_SMS_CODE;

    private final KeycloakSession session;

    KeycloakSmsAuthenticatorCredentialProvider(final KeycloakSession session) {
        this.session = session;
    }

    private CredentialModel getSecret(final RealmModel realm, final UserModel user) {
        CredentialModel secret = null;
        if (user instanceof CachedUserModel) {
            final CachedUserModel cached = (CachedUserModel) user;
            secret = (CredentialModel) cached.getCachedWith().get(CACHE_KEY);

        } else {
            final List<CredentialModel> creds = session.userCredentialManager().getStoredCredentialsByType(realm, user, KeycloakSmsConstants.USR_CRED_MDL_SMS_CODE);
            if (!creds.isEmpty()) {
                secret = creds.get(0);
            }
        }
        return secret;
    }


    @Override
    public boolean updateCredential(final RealmModel realm, final UserModel user, final CredentialInput input) {
        if (!KeycloakSmsConstants.USR_CRED_MDL_SMS_CODE.equals(input.getType())) {
            return false;
        }
        if (!(input instanceof UserCredentialModel)) {
            return false;
        }
        final UserCredentialModel credInput = (UserCredentialModel) input;
        final List<CredentialModel> creds = session.userCredentialManager().getStoredCredentialsByType(realm, user, KeycloakSmsConstants.USR_CRED_MDL_SMS_CODE);
        if (creds.isEmpty()) {
            final CredentialModel secret = new CredentialModel();
            secret.setType(KeycloakSmsConstants.USR_CRED_MDL_SMS_CODE);
            secret.setValue(credInput.getValue());
            secret.setCreatedDate(Time.currentTimeMillis());
            session.userCredentialManager().createCredential(realm, user, secret);
        } else {
            creds.get(0).setValue(credInput.getValue());
            session.userCredentialManager().updateCredential(realm, user, creds.get(0));
        }
        session.userCache().evict(realm, user);
        return true;
    }

    @Override
    public void disableCredentialType(final RealmModel realm, final UserModel user, final String credentialType) {
        if (!KeycloakSmsConstants.USR_CRED_MDL_SMS_CODE.equals(credentialType)) {
            return;
        }
        session.userCredentialManager().disableCredentialType(realm, user, credentialType);
        session.userCache().evict(realm, user);

    }

    @Override
    public Set<String> getDisableableCredentialTypes(final RealmModel realm, final UserModel user) {
        if (!session.userCredentialManager().getStoredCredentialsByType(realm, user, KeycloakSmsConstants.USR_CRED_MDL_SMS_CODE).isEmpty()) {
            final Set<String> set = new HashSet<>();
            set.add(KeycloakSmsConstants.USR_CRED_MDL_SMS_CODE);
            return set;
        } else {
            return Collections.emptySet();
        }

    }

    @Override
    public boolean supportsCredentialType(final String credentialType) {
        return KeycloakSmsConstants.USR_CRED_MDL_SMS_CODE.equals(credentialType);
    }

    @Override
    public boolean isConfiguredFor(final RealmModel realm, final UserModel user, final String credentialType) {
        return KeycloakSmsConstants.USR_CRED_MDL_SMS_CODE.equals(credentialType) && getSecret(realm, user) != null;
    }

    @Override
    public boolean isValid(final RealmModel realm, final UserModel user, final CredentialInput input) {
        if (!KeycloakSmsConstants.USR_CRED_MDL_SMS_CODE.equals(input.getType())) {
            return false;
        }
        if (!(input instanceof UserCredentialModel)) {
            return false;
        }

        final String secret = getSecret(realm, user).getValue();

        return secret != null && ((UserCredentialModel) input).getValue().equals(secret);
    }

    @Override
    public void onCache(final RealmModel realm, final CachedUserModel user, final UserModel delegate) {
        final List<CredentialModel> creds = session.userCredentialManager().getStoredCredentialsByType(realm, user, KeycloakSmsConstants.USR_CRED_MDL_SMS_CODE);
        if (!creds.isEmpty()) {
            user.getCachedWith().put(CACHE_KEY, creds.get(0));
        }
    }
}
