package org.example;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

public class SqsService {



    private static final SqsClient client = SqsClient.builder()
            .region(Region.US_EAST_1)
            .build();


    public static SqsClient getClient() {
        return client;
    }


    public static String getSQSQueue(String queueName){

        try {
            GetQueueUrlResponse response = client.getQueueUrl(
                    GetQueueUrlRequest.builder()
                            .queueName(queueName)
                            .build()
            );

            return response.queueUrl();
        } catch (QueueDoesNotExistException e) {
            //create the queue
            Logger.getLogger().log("SQS Queue " + queueName + " does not exist.");
            CreateQueueResponse response = client.createQueue(CreateQueueRequest.builder()
                    .queueName(queueName)
                    .build());
            return response.queueUrl();

        }

    }

    public boolean checkIfQueueExists(String queueName){
        try {
            client.getQueueUrl(
                    GetQueueUrlRequest.builder()
                            .queueName(queueName)
                            .build()
            );
            return true;
        } catch (QueueDoesNotExistException e) {
            return false;
        }
    }

    public String createQueue(String queueName){
        client.createQueue(CreateQueueRequest.builder()
                .queueName(queueName)
                .build());
        Logger.getLogger().log("SQS Queue " + queueName + " created.");
        return getSQSQueue(queueName);
    }

    public void sendMessage(String queueUrl, String messageBody){
        //first check if queue exists
        if (!checkIfQueueExists(queueUrl)){
            Logger.getLogger().log("Queue " + queueUrl + " does not exist. Cannot send message.");
            createQueue(queueUrl);
        }
        SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .build();

        client.sendMessage(sendMsgRequest);
        Logger.getLogger().log("Message sent to SQS: " + messageBody);
    }
}
