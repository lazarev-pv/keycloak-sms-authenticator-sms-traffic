package six.six.gateway.aws.snsclient;

import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import six.six.gateway.SMSService;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by nickpack on 09/08/2017.
 */
public class SnsNotificationService implements SMSService {

    //TODO Implement proxy

    @Override
    public boolean send(final String phoneNumber, final String message, final String clientToken, final String clientSecret) {
        final Map<String, MessageAttributeValue> smsAttributes = new HashMap<>();
        smsAttributes.put("AWS.SNS.SMS.SenderID", new MessageAttributeValue()
                .withStringValue("HomeOffice")
                .withDataType("String"));

        final String id= SnsClientFactory.getSnsClient(clientToken, clientSecret).publish(new PublishRequest()
                .withMessage(message)
                .withPhoneNumber(phoneNumber)
                .withMessageAttributes(smsAttributes)).getMessageId();

        return (id!=null);
    }
}
