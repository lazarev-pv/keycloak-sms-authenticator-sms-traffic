package six.six.gateway.aws.snsclient;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;

/**
 * Created by nickpack on 09/08/2017.
 */
class SnsClientFactory {
    private static AmazonSNS snsClient;

    static AmazonSNS getSnsClient(final String clientToken, final String clientSecret) {
        if (null == snsClient) {
            final BasicAWSCredentials CREDENTIALS = new BasicAWSCredentials(clientToken, clientSecret);
            snsClient = AmazonSNSClientBuilder
                                .standard()
                                .withCredentials(new AWSStaticCredentialsProvider(CREDENTIALS))
                                .withRegion(Regions.EU_WEST_1)
                                .build();

        }
        return snsClient;
    }
}
