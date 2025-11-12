package org.example;
import org.w3c.dom.Text;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.*;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.InstanceType;
import software.amazon.awssdk.services.ec2.model.RunInstancesRequest;
import software.amazon.awssdk.services.ec2.model.RunInstancesResponse;
import software.amazon.awssdk.services.s3.*;

import java.util.List;

public class Main {
    static Main instance;
    public static void main(String[] args) {
        System.out.println("Hello world!");
        try {
            Main app = getApp();
            app.start();
        } catch (Exception e) {
            e.printStackTrace();
            print("Failed to start EC2 instance");
        }


    }

    public static Main getApp(){
        if (instance == null){
            instance = new Main();
        }
        return instance;
    }

    public void start(){
        Ec2Client ec2 = Ec2Client
                .builder()
                .region(Region.US_EAST_1)   // pick your region
                .build();
        String amiId =  "ami-076515f20540e6e0b"; // Linux and Java 1.8
        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .instanceType(InstanceType.T1_MICRO)
                .imageId(amiId)
                .maxCount(1)
                .minCount(1)
//                .userData(Base64.getEncoder().encodeToString(
//                        Text)
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

    public static void print(String message){
        System.out.println(message);
    }


    enum analysisTypes{
        POS, CONSTITUENCY, DEPENDENCY
    }


}