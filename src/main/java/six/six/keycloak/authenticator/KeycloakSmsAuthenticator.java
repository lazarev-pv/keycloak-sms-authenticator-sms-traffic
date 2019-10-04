package six.six.keycloak.authenticator;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import six.six.keycloak.KeycloakSmsConstants;
import six.six.keycloak.requiredaction.action.required.KeycloakSmsMobilenumberRequiredAction;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.util.Date;
import java.util.List;

/**
 * Created by joris on 11/11/2016.
 */
public class KeycloakSmsAuthenticator implements Authenticator {

    private static final Logger logger = Logger.getLogger(KeycloakSmsAuthenticator.class);

    public static final String CREDENTIAL_TYPE = "sms_validation";

    private enum CODE_STATUS {
        VALID,
        INVALID,
        EXPIRED
    }


    private boolean isOnlyForVerificationMode(final boolean onlyForVerification, final String mobileNumber, final String mobileNumberVerified){
        return (mobileNumber ==null || onlyForVerification==true && !mobileNumber.equals(mobileNumberVerified) );
    }

    private String getMobileNumber(final UserModel user){
        final List<String> mobileNumberCreds = user.getAttribute(KeycloakSmsConstants.ATTR_MOBILE);

        String mobileNumber = null;
        if (mobileNumberCreds != null && !mobileNumberCreds.isEmpty()) {
            mobileNumber = mobileNumberCreds.get(0);
        }

        return  mobileNumber;
    }

    private String getMobileNumberVerified(final UserModel user){
        final List<String> mobileNumberVerifieds = user.getAttribute(KeycloakSmsConstants.ATTR_MOBILE_VERIFIED);

        String mobileNumberVerified = null;
        if (mobileNumberVerifieds != null && !mobileNumberVerifieds.isEmpty()) {
            mobileNumberVerified = mobileNumberVerifieds.get(0);
        }
        return  mobileNumberVerified;
    }

    @Override
    public void authenticate(final AuthenticationFlowContext context) {
        logger.debug("authenticate called ... context = " + context);
        final UserModel user = context.getUser();
        final AuthenticatorConfigModel config = context.getAuthenticatorConfig();

        final boolean onlyForVerification=KeycloakSmsAuthenticatorUtil.getConfigBoolean(config, KeycloakSmsConstants.MOBILE_VERIFICATION_ENABLED);

        final String mobileNumber =getMobileNumber(user);
        final String mobileNumberVerified = getMobileNumberVerified(user);

        if (onlyForVerification==false || isOnlyForVerificationMode(onlyForVerification, mobileNumber,mobileNumberVerified)){
            if (mobileNumber != null) {
                // The mobile number is configured --> send an SMS
                final long nrOfDigits = KeycloakSmsAuthenticatorUtil.getConfigLong(config, KeycloakSmsConstants.CONF_PRP_SMS_CODE_LENGTH, 8L);
                logger.debug("Using nrOfDigits " + nrOfDigits);


                final long ttl = KeycloakSmsAuthenticatorUtil.getConfigLong(config, KeycloakSmsConstants.CONF_PRP_SMS_CODE_TTL, 10 * 60L); // 10 minutes in s

                logger.debug("Using ttl " + ttl + " (s)");

                final String code = KeycloakSmsAuthenticatorUtil.getSmsCode(nrOfDigits);

                storeSMSCode(context, code, new Date().getTime() + (ttl * 1000)); // s --> ms
                if (KeycloakSmsAuthenticatorUtil.sendSmsCode(mobileNumber, code, context)) {
                    final Response challenge = context.form().createForm("sms-validation.ftl");
                    context.challenge(challenge);
                } else {
                    final Response challenge = context.form()
                                                      .setError("sms-auth.not.send")
                                                      .createForm("sms-validation-error.ftl");
                    context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR, challenge);
                }
            } else {
                final boolean isAskingFor=KeycloakSmsAuthenticatorUtil.getConfigBoolean(config, KeycloakSmsConstants.MOBILE_ASKFOR_ENABLED);
                if(isAskingFor){
                    //Enable access and ask for mobilenumber
                    user.addRequiredAction(KeycloakSmsMobilenumberRequiredAction.PROVIDER_ID);
                    context.success();
                }else {
                    // The mobile number is NOT configured --> complain
                    final Response challenge = context.form()
                                                      .setError("sms-auth.not.mobile")
                                                      .createForm("sms-validation-error.ftl");
                    context.failureChallenge(AuthenticationFlowError.CLIENT_CREDENTIALS_SETUP_REQUIRED, challenge);
                }
            }
        }else{
            logger.debug("Skip SMS code because onlyForVerification " + onlyForVerification + " or  mobileNumber==mobileNumberVerified");
            context.success();

        }
    }

    @Override
    public void action(final AuthenticationFlowContext context) {
        logger.debug("action called ... context = " + context);
        final CODE_STATUS status = validateCode(context);
        Response challenge = null;
        switch (status) {
            case EXPIRED:
                challenge = context.form()
                        .setError("sms-auth.code.expired")
                        .createForm("sms-validation.ftl");
                context.failureChallenge(AuthenticationFlowError.EXPIRED_CODE, challenge);
                break;

            case INVALID:
                if (context.getExecution().getRequirement() == AuthenticationExecutionModel.Requirement.OPTIONAL ||
                        context.getExecution().getRequirement() == AuthenticationExecutionModel.Requirement.ALTERNATIVE) {
                    logger.debug("Calling context.attempted()");
                    context.attempted();
                } else if (context.getExecution().getRequirement() == AuthenticationExecutionModel.Requirement.REQUIRED) {
                    challenge = context.form()
                            .setError("sms-auth.code.invalid")
                            .createForm("sms-validation.ftl");
                    context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
                } else {
                    // Something strange happened
                    logger.warn("Undefined execution ...");
                }
                break;

            case VALID:
                context.success();
                updateVerifiedMobilenumber(context);
                break;

        }
    }

    /**
     * If necessary update verified mobilenumber
     * @param context
     */
    private void updateVerifiedMobilenumber(final AuthenticationFlowContext context){
        final AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        final UserModel user = context.getUser();
        final boolean onlyForVerification=KeycloakSmsAuthenticatorUtil.getConfigBoolean(config, KeycloakSmsConstants.MOBILE_VERIFICATION_ENABLED);

        if(onlyForVerification){
            //Only verification mode
            final List<String> mobileNumberCreds = user.getAttribute(KeycloakSmsConstants.ATTR_MOBILE);
            if (mobileNumberCreds != null && !mobileNumberCreds.isEmpty()) {
                user.setAttribute(KeycloakSmsConstants.ATTR_MOBILE_VERIFIED,mobileNumberCreds);
            }
        }
    }

    // Store the code + expiration time in a UserCredential. Keycloak will persist these in the DB.
    // When the code is validated on another node (in a clustered environment) the other nodes have access to it's values too.
    private void storeSMSCode(final AuthenticationFlowContext context, final String code, final Long expiringAt) {
        final UserCredentialModel credentials = new UserCredentialModel();
        credentials.setType(KeycloakSmsConstants.USR_CRED_MDL_SMS_CODE);
        credentials.setValue(code);

        context.getSession().userCredentialManager().updateCredential(context.getRealm(), context.getUser(), credentials);

        credentials.setType(KeycloakSmsConstants.USR_CRED_MDL_SMS_EXP_TIME);
        credentials.setValue((expiringAt).toString());
        context.getSession().userCredentialManager().updateCredential(context.getRealm(), context.getUser(), credentials);
    }


    protected CODE_STATUS validateCode(final AuthenticationFlowContext context) {
        CODE_STATUS result = CODE_STATUS.INVALID;

        logger.debug("validateCode called ... ");
        final MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        final String enteredCode = formData.getFirst(KeycloakSmsConstants.ANSW_SMS_CODE);
        final KeycloakSession session = context.getSession();

        final List codeCreds = session.userCredentialManager().getStoredCredentialsByType(context.getRealm(), context.getUser(), KeycloakSmsConstants.USR_CRED_MDL_SMS_CODE);
        /*List timeCreds = session.userCredentialManager().getStoredCredentialsByType(context.getRealm(), context.getUser(), KeycloakSmsAuthenticatorConstants.USR_CRED_MDL_SMS_EXP_TIME);*/

        final CredentialModel expectedCode = (CredentialModel) codeCreds.get(0);
        /*CredentialModel expTimeString = (CredentialModel) timeCreds.get(0);*/

        logger.debug("Expected code = " + expectedCode + "    entered code = " + enteredCode);

        if (expectedCode != null) {
            result = enteredCode.equals(expectedCode.getValue()) ? CODE_STATUS.VALID : CODE_STATUS.INVALID;
            /*long now = new Date().getTime();

            logger.debug("Valid code expires in " + (Long.parseLong(expTimeString.getValue()) - now) + " ms");
            if (result == CODE_STATUS.VALID) {
                if (Long.parseLong(expTimeString.getValue()) < now) {
                    logger.debug("Code is expired !!");
                    result = CODE_STATUS.EXPIRED;
                }
            }*/
        }
        logger.debug("result : " + result);
        return result;
    }
    @Override
    public boolean requiresUser() {
        logger.debug("requiresUser called ... returning true");
        return true;
    }
    @Override
    public boolean configuredFor(final KeycloakSession session, final RealmModel realm, final UserModel user) {
        logger.debug("configuredFor called ... session=" + session + ", realm=" + realm + ", user=" + user);
        return true;
    }

    @Override
    public void setRequiredActions(final KeycloakSession session, final RealmModel realm, final UserModel user) {
        logger.debug("setRequiredActions called ... session=" + session + ", realm=" + realm + ", user=" + user);
    }
    @Override
    public void close() {
        logger.debug("close called ...");
    }

}
