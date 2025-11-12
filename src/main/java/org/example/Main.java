package org.example;
import org.w3c.dom.Text;
import software.amazon.awssdk.services.ec2.*;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.InstanceType;
import software.amazon.awssdk.services.ec2.model.RunInstancesRequest;
import software.amazon.awssdk.services.ec2.model.RunInstancesResponse;
import software.amazon.awssdk.services.s3.*;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello world!");


    }

    public void start(){
        Ec2Client ec2 = Ec2Client.create();
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


    }
}



enum analysisTypes{
    POS, CONSTITUENCY, DEPENDENCY
}