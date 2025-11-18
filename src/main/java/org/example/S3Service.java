package org.example;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.sqs.model.Message;

import java.io.File;

public class S3Service {

    //static instance
    static S3Service instance;

    static String bucketName = "yehuda-awsremote-20251113";


    static S3Client s3 = S3Client.builder()
            .region(Region.US_EAST_1)
            .build();

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

}
