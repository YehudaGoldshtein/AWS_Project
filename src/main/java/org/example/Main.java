package org.example;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.*;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
    public static final String MANAGER_REQUEST_QUEUE = "ManagerRequestQueue";
    static String bucketName = "yehuda-awsremote-20251113";
    static String managerTag = "ManagerInstance";
    static String managerAmiId =  "ami-076515f20540e6e0b"; // Linux and Java 1.8


    public static void main(String[] args) {
        Map<terminalParams, String> terminalParamsMap = parseArgs(args);
        if (terminalParamsMap == null){
            Logger.getLogger().log("Invalid arguments. Usage: <file_path> <analysis_type> <file_cap> [terminate]");
            return;
        }
        String filePath = terminalParamsMap.get(terminalParams.FILE_PATH);

        File file = new File(filePath);
        if (!file.exists()){
            Logger.getLogger().log("File does not exist: " + filePath + " in path" + file.getAbsolutePath());
            return;
        }
        Instance managerInstance = getManager();
        if (managerInstance == null){
            Logger.getLogger().log("Manager is already up. Exiting.");
            setupManager();
        }

        SqsClient sqsClient = SqsClient.builder()
                .region(Region.US_EAST_1)
                .build();
        String requestQueueUrl = getSQSQueue(sqsClient, MANAGER_REQUEST_QUEUE);
        Logger.getLogger().log("Setup complete. sending file to manager...");
        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(requestQueueUrl)
                .messageBody(taskTypes.ANALYZE + ";" + file.getName())
                .build();

        sqsClient.sendMessage(request);
        Logger.getLogger().log("File sent to manager: " + file.getName());

        //check every second if the result file is in S3 by looking for a "Done" message in the SQS
        while (true){
            ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                    .queueUrl(requestQueueUrl)
                    .maxNumberOfMessages(1)
                    .waitTimeSeconds(1)
                    .build();

            List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).messages();
            if (!messages.isEmpty()){
                for (Message message : messages) {
                    if (message.body().equals(taskTypes.DONE + ";" + file.getName())){
                        Logger.getLogger().log("Analysis complete for file: " + file.getName());
                        //delete the message
                        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                                .queueUrl(requestQueueUrl)
                                .receiptHandle(message.receiptHandle())
                                .build());
                        handleCompletion(file.getName(), message);
                        return;
                    }
                    else if (message.body().equals(taskTypes.ERROR + ";" + file.getName())){
                        Logger.getLogger().log("Analysis error for file: " + file.getName());
                        //delete the message
                        //Todo: download error log from S3, reschedule e.t.c.
                        return;
                    }
                }
            }
        }



    }

    enum terminalParams{
        FILE_PATH, ANALYSIS_TYPE, FILE_CAP, TERMINATE
    }

    public enum taskTypes{
        ANALYZE, TERMINATE, DONE, ERROR
    }


    enum analysisTypes{
        POS, CONSTITUENCY, DEPENDENCY
    }

    private static Map<terminalParams, String> parseArgs(String[] args){
        if (args == null || args.length < 3 || args.length > 4){
            return null;
        }
        Map<terminalParams, String> params = new java.util.HashMap<>();
        params.put(terminalParams.FILE_PATH, args[0]);
        params.put(terminalParams.ANALYSIS_TYPE, args[1]);
        params.put(terminalParams.FILE_CAP, args[2]);
        if (args.length == 4){
            params.put(terminalParams.TERMINATE, args[3]);
        }
        return params;
    }

    public static Instance getManager(){
        Instance managerInstance = null;
        List<Filter> filters = new ArrayList<>();

        filters.add(Filter.builder()
                .name("tag:Role")
                .values(managerTag)
                .build());

        filters.add(Filter.builder()
                .name("instance-state-name")
                .values(InstanceStateName.RUNNING.toString())  // "running"
                .build());

        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                .filters(filters)
                .build();

        DescribeInstancesResponse response = Ec2Client.create().describeInstances(request);

        for (Reservation reservation : response.reservations()) {
            for (Instance instance : reservation.instances()) {
                Logger.getLogger().log("Found running manager instance: " + instance.instanceId());
                managerInstance = instance;
                break;
            }
        }

        Logger.getLogger().log("No running manager instance found.");
        return managerInstance;
    }

    public static void setupManager(){
        IamInstanceProfileSpecification profile =
                IamInstanceProfileSpecification.builder()
                        .name("EMR_EC2_DefaultRole")  // or whatever role name you picked
                        .build();

        Ec2Client ec2 = Ec2Client
                .builder()
                .region(Region.US_EAST_1)   // pick your region
                .build();
        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .instanceType(InstanceType.T1_MICRO)
                .imageId(managerAmiId)
                .iamInstanceProfile(profile)
                .maxCount(1)
                .minCount(1)
                .tagSpecifications(TagSpecification.builder()
                        .resourceType(ResourceType.INSTANCE)
                        .tags(Tag.builder()
                                .key("Role")
                                .value(managerTag)
                                .build())
                        .build())
                .build();

        RunInstancesResponse response = ec2.runInstances(runRequest);

        List<Instance> instances = response.instances();
        for (Instance instance : instances) {
            System.out.printf(
                    "Successfully started EC2 instance %s based on AMI %s",
                    instance.instanceId(),
                    instance.imageId());
        }

    }

    public static String getSQSQueue(SqsClient client, String queueName){

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

    public static void handleCompletion(String fileName, Message message){
        S3Client s3 = S3Client.builder()
                .region(Region.US_EAST_1)
                .build();

        String key = message.body().split(";")[1] + "_output.txt";
        File outputFile = new File("output_" + fileName);
        try {
            s3.getObject(GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .build(),
                    outputFile.toPath());
            Logger.getLogger().log("Output file downloaded: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            Logger.getLogger().log("Error downloading output file: " + e.getMessage());
        }
    }


}