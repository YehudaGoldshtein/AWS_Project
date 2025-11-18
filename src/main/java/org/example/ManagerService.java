package org.example;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class ManagerService {

    public static final String MANAGER_ROLE = "EMR_EC2_DefaultRole";
    static final String managerTag = "ManagerInstance";
    static final String managerAmiId =  "ami-071e30579bb36ab78"; // with java 17, more logs

    public static final String MANAGER_REQUEST_QUEUE = "ManagerRequestQueue";

    static final String userDataScript =
            "#!/bin/bash\n" +
                    "cd /home/ec2-user\n" +
                    "nohup java -jar AWSRemote-1.0-SNAPSHOT-shaded.jar > manager.log 2>&1 &\n";


    static final String userDataBase64 = Base64.getEncoder()
            .encodeToString(userDataScript.getBytes(StandardCharsets.UTF_8));

    public static Ec2Client ec2 = Ec2Client
            .builder()
            .region(Region.US_EAST_1)   // pick your region
            .build();

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
        if (managerInstance == null){
            managerInstance = setupManager();
        }
        return managerInstance;
    }

    public static Instance setupManager(){
        IamInstanceProfileSpecification profile =
                IamInstanceProfileSpecification.builder()
                        .name(MANAGER_ROLE)  // or whatever role name you picked
                        .build();


        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .instanceType(InstanceType.T1_MICRO)
                .imageId(managerAmiId)
                .iamInstanceProfile(profile)
                .maxCount(1)
                .minCount(1)
                .userData(userDataBase64)
                .metadataOptions(InstanceMetadataOptionsRequest.builder()
                        .instanceMetadataTags(InstanceMetadataTagsState.ENABLED) // <-- this line
                        .build())
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
            Logger.getLogger().log(
                    "Successfully started EC2 instance " + instance.instanceId() + " based on AMI " + instance.imageId());
            return instance;
        }

        return null;
    }
}
