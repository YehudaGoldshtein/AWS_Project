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
        ClientApp.run(args);
    }

    //that should be the entire main class,
    // the rest of the code here is for reference
    // only until all responsibilities are moved to their own classes

    enum terminalParams{
        FILE_PATH, ANALYSIS_TYPE, FILE_CAP, TERMINATE
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

    public static String uploadFile(File file){
        S3Client s3 = S3Client.builder()
                .region(Region.US_EAST_1)
                .build();

        String key = file.getName();
        try {
            //i manually created this bucket in AWS console
            s3.putObject(builder -> builder.bucket(bucketName).key(key).build(),
                    file.toPath());
            Logger.getLogger().log("File uploaded to S3: " + key);
            return "s3://" + bucketName + "/" + key;
        } catch (Exception e) {
            Logger.getLogger().log("Error uploading file to S3: " + e.getMessage());
            return null;
        }
    }

    public static analysisTypes getAnalysisType(String analysisType){
        switch (analysisType.toLowerCase()){
            case "pos":
                return analysisTypes.POS;
            case "constituency":
                return analysisTypes.CONSTITUENCY;
            case "dependency":
                return analysisTypes.DEPENDENCY;
            default:
                return null;
        }
    }


}