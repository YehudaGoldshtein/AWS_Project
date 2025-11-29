package org.example;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class ManagerService {

    public static final String MANAGER_ROLE = "EMR_EC2_DefaultRole";
    static final String MANAGER_TAG = "ManagerInstance";
    //static final String MANAGER_AMI_ID = "ami-0829baff2115b1acb"; // with java 17, more (Yehuda)
    static final String MANAGER_AMI_ID = "ami-092943a104c8c34a5"; // will be dynamic from now on (Gal)

//  static final String WORKER_AMI =  "ami-0326bf6e2eb8642aa"; // replace this with actual worker AMI ID, (Yehuda)
    static final String WORKER_AMI = "ami-04a5572b615ab615d"; // will be dynamic from now on (Gal)


    // make sure it's not manager AMI for it will cuase an infinite loop of machines creating machines
    public static final String LOCAL_TO_MANAGER_REQUEST_QUEUE = "LocalToManagerRequestQueue";
    public static final String MANAGER_TO_LOCAL_REQUEST_QUEUE = "ManagerToLocalRequestQueue";
    static final String jarName = "AWSManager-1.0-SNAPSHOT.jar"; 
    public static Ec2Client ec2 = Ec2Client
            .builder()
            .region(Region.US_EAST_1)   // pick your region
            .build();

    public static Instance getManager(boolean CreateIfNotExists){
        //first check if a manager instance is already running
        Instance existingInstance = new ManagerService().getExisitingInstance();
        if (existingInstance != null || !CreateIfNotExists){
            return existingInstance;
        }
        //else create a new one
        Logger.getLogger().log("No running manager instance found.");
        String managerUserData = getManagerUserData();
        if (managerUserData == null){
            Logger.getLogger().log("Failed to get manager user data.");
            return null;
        }
        return  setupManager(managerUserData);

    }

    public static Instance setupManager(String userData){
        IamInstanceProfileSpecification profile =
                IamInstanceProfileSpecification.builder()
                        .name(MANAGER_ROLE)  // or whatever role name you picked
                        .build();


        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .instanceType(InstanceType.T1_MICRO)
                .imageId(MANAGER_AMI_ID)
                .iamInstanceProfile(profile)
                .maxCount(1)
                .minCount(1)
                .userData(userData)
                .metadataOptions(InstanceMetadataOptionsRequest.builder()
                        .instanceMetadataTags(InstanceMetadataTagsState.ENABLED) // <-- this line
                        .build())
                .tagSpecifications(TagSpecification.builder()
                        .resourceType(ResourceType.INSTANCE)
                        .tags(Tag.builder()
                                .key("Role")
                                .value(MANAGER_TAG)
                                .build())
                        .build())
                .build();

        RunInstancesResponse response = ec2.runInstances(runRequest);

        List<Instance> instances = response.instances();
        for (Instance instance : instances) {
            Logger.getLogger().log(
                    "Successfully started EC2 instance " + instance.instanceId() + " based on AMI " + instance.imageId());
            return instance;
        }

        return null;
    }


    public static void terminateManager() {
        Instance manager = getManager(false);
        if (manager != null) {
            TerminateInstancesRequest terminateRequest = TerminateInstancesRequest.builder()
                    .instanceIds(manager.instanceId())
                    .build();

            ec2.terminateInstances(terminateRequest);
            Logger.getLogger().log("Terminated manager instance: " + manager.instanceId());
        } else {
            Logger.getLogger().log("No manager instance found to terminate.");
        }
    }

    Instance getExisitingInstance(){
        List<Filter> filters = new ArrayList<>();
        filters.add(Filter.builder()
                .name("tag:Role")
                .values(MANAGER_TAG)
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
                return instance;
            }
        }
        return null;
    }

    static String getManagerUserData(){
        ProfileCredentialsProvider credentialsProvider =
                ProfileCredentialsProvider.builder()
                        .profileName("default")
                        .build();

        // 3. Resolve the credentials
        // This loads the actual values from the file.
        AwsCredentials credentials = credentialsProvider.resolveCredentials();
        String updatedUserData = "";
        if (credentials instanceof AwsSessionCredentials) {
            // Cast to the type that guarantees the session token method exists
            AwsSessionCredentials sessionCredentials = (AwsSessionCredentials) credentials;
            String sessionToken = sessionCredentials.sessionToken();
            updatedUserData =  "#!/bin/bash\n" +
                    "cd /home/ec2-user\n" +
                    "nohup java -jar " + jarName + " " +
                    credentials.accessKeyId() + " " +
                    credentials.secretAccessKey() + " " +
                    sessionToken + " " +
                    WORKER_AMI + " " +
                    " > manager.log 2>&1 &\n";
//            Logger.getLogger().log("starting to setup manager with user data: \n" + updatedUserData);
            return Base64.getEncoder()
                    .encodeToString(updatedUserData.getBytes(StandardCharsets.UTF_8));
        }
        else return null;
    }
}
