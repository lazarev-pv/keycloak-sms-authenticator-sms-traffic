package six.six.keycloak.authenticator;


import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.UserModel;
import org.keycloak.theme.Theme;
import org.keycloak.theme.ThemeProvider;
import six.six.gateway.Gateways;
import six.six.gateway.SMSService;
import six.six.gateway.aws.snsclient.SnsNotificationService;
import six.six.gateway.lyrasms.LyraSMSService;
import six.six.keycloak.EnvSubstitutor;
import six.six.keycloak.KeycloakSmsConstants;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Created by joris on 18/11/2016.
 */
public class KeycloakSmsAuthenticatorUtil {

    private static final Logger logger = Logger.getLogger(KeycloakSmsAuthenticatorUtil.class);

    public static String getAttributeValue(final UserModel user, final String attributeName) {
        String result = null;
        final List<String> values = user.getAttribute(attributeName);
        if (values != null && values.size() > 0) {
            result = values.get(0);
        }

        return result;
    }

    public static String getConfigString(final AuthenticatorConfigModel config, final String configName) {
        return getConfigString(config, configName, null);
    }

    public static String getConfigString(final AuthenticatorConfigModel config, final String configName, final String defaultValue) {

        String value = defaultValue;

        if (config.getConfig() != null) {
            // Get value
            value = config.getConfig().get(configName);
        }

        return value;
    }

    public static Long getConfigLong(final AuthenticatorConfigModel config, final String configName) {
        return getConfigLong(config, configName, null);
    }

    public static Long getConfigLong(final AuthenticatorConfigModel config, final String configName, final Long defaultValue) {

        Long value = defaultValue;

        if (config.getConfig() != null) {
            // Get value
            final Object obj = config.getConfig().get(configName);
            try {
                value = Long.valueOf((String) obj); // s --> ms
            } catch (final NumberFormatException nfe) {
                logger.error("Can not convert " + obj + " to a number.");
            }
        }

        return value;
    }

    public static Boolean getConfigBoolean(final AuthenticatorConfigModel config, final String configName) {
        return getConfigBoolean(config, configName, true);
    }

    public static Boolean getConfigBoolean(final AuthenticatorConfigModel config, final String configName, final Boolean defaultValue) {

        Boolean value = defaultValue;

        if (config.getConfig() != null) {
            // Get value
            final Object obj = config.getConfig().get(configName);
            try {
                value = Boolean.valueOf((String) obj); // s --> ms
            } catch (final NumberFormatException nfe) {
                logger.error("Can not convert " + obj + " to a boolean.");
            }
        }

        return value;
    }

    public static String createMessage(String text, final String code, final String mobileNumber) {
        if(text !=null){
            text = text.replaceAll("%sms-code%", code);
            text = text.replaceAll("%phonenumber%", mobileNumber);
        }
        return text;
    }

    public static String setDefaultCountryCodeIfZero(String mobileNumber, final String prefix , final String condition) {

        if (prefix!=null && condition!=null && mobileNumber.startsWith(condition)) {
            mobileNumber = prefix + mobileNumber.substring(1);
        }
        return mobileNumber;
    }

    /**
     * Check mobile number normative strcuture
     * @param mobileNumber
     * @return formatted mobile number
     */
    public static String checkMobileNumber(String mobileNumber) {

        final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        try {
            final Phonenumber.PhoneNumber phone = phoneUtil.parse(mobileNumber, null);
            mobileNumber = phoneUtil.format(phone,
                    PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (final NumberParseException e) {
            logger.error("Invalid phone number " + mobileNumber, e);
        }

        return mobileNumber;
    }


    public static String getMessage(final AuthenticationFlowContext context, final String key){
        String result=null;
        try {
            final ThemeProvider themeProvider = context.getSession().getProvider(ThemeProvider.class, "extending");
            final Theme currentTheme = themeProvider.getTheme(context.getRealm().getLoginTheme(), Theme.Type.LOGIN);
            final Locale locale = context.getSession().getContext().resolveLocale(context.getUser());
            result = currentTheme.getMessages(locale).getProperty(key);
        }catch (final IOException e){
            logger.warn(key + "not found in messages");
        }
        return result;
    }

    public static String getMessage(final RequiredActionContext context, final String key){
        String result=null;
        try {
            final ThemeProvider themeProvider = context.getSession().getProvider(ThemeProvider.class, "extending");
            final Theme currentTheme = themeProvider.getTheme(context.getRealm().getLoginTheme(), Theme.Type.LOGIN);
            final Locale locale = context.getSession().getContext().resolveLocale(context.getUser());
            result = currentTheme.getMessages(locale).getProperty(key);
        }catch (final IOException e){
            logger.warn(key + "not found in messages");
        }
        return result;
    }


    static boolean sendSmsCode(final String mobileNumber, final String code, final AuthenticationFlowContext context) {
        final AuthenticatorConfigModel config = context.getAuthenticatorConfig();

        // Send an SMS
        KeycloakSmsAuthenticatorUtil.logger.debug("Sending " + code + "  to mobileNumber " + mobileNumber);

        final String smsUsr = EnvSubstitutor.envSubstitutor.replace(getConfigString(config, KeycloakSmsConstants.CONF_PRP_SMS_CLIENTTOKEN));
        final String smsPwd = EnvSubstitutor.envSubstitutor.replace(getConfigString(config, KeycloakSmsConstants.CONF_PRP_SMS_CLIENTSECRET));
        final String gateway = getConfigString(config, KeycloakSmsConstants.CONF_PRP_SMS_GATEWAY);
        final String endpoint = EnvSubstitutor.envSubstitutor.replace(getConfigString(config, KeycloakSmsConstants.CONF_PRP_SMS_GATEWAY_ENDPOINT));
        final boolean isProxy = getConfigBoolean(config, KeycloakSmsConstants.PROXY_ENABLED);

        final String template =getMessage(context, KeycloakSmsConstants.CONF_PRP_SMS_TEXT);

        final String smsText = createMessage(template,code, mobileNumber);
        final boolean result;
        final SMSService smsService;
        try {
            final Gateways g=Gateways.valueOf(gateway);
            switch(g) {
                case LYRA_SMS:
                    smsService=new LyraSMSService(endpoint,isProxy);
                    break;
                default:
                    smsService=new SnsNotificationService();
            }

            result=smsService.send(checkMobileNumber(setDefaultCountryCodeIfZero(mobileNumber, getMessage(context, KeycloakSmsConstants.MSG_MOBILE_PREFIX_DEFAULT), getMessage(context, KeycloakSmsConstants.MSG_MOBILE_PREFIX_CONDITION))), smsText, smsUsr, smsPwd);
          return result;
       } catch(final Exception e) {
            logger.error("Fail to send SMS " ,e );
            return false;
        }
    }

    static String getSmsCode(final long nrOfDigits) {
        if (nrOfDigits < 1) {
            throw new RuntimeException("Number of digits must be bigger than 0");
        }

        final double maxValue = Math.pow(10.0, nrOfDigits); // 10 ^ nrOfDigits;
        final Random r = new Random();
        final long code = (long) (r.nextFloat() * maxValue);
        return Long.toString(code);
    }

    public static boolean validateTelephoneNumber(final String telephoneNumber, final String regexp ) {
        boolean result=true;
        if(regexp!=null){
            result =telephoneNumber.matches(regexp);
        }

        return result;
    }
}
