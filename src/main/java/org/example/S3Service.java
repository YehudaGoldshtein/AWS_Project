package org.example;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.sqs.model.Message;

import java.io.File;

public class S3Service {

    //static instance
    static S3Service instance;
    // static String bucketName = "yehuda-awsremote-20251113";
    static String bucketName = "aws-bucket-project-workers";

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

    /**
     * Download HTML file from S3 and save to local file
     * @param htmlS3Url S3 URL in format "s3://bucket/key"
     * @param outputFilePath Local file path to save the HTML file
     * @return true if successful, false otherwise
     */
    public static boolean downloadHTMLFile(String htmlS3Url, String outputFilePath) {
        try {
            // Parse S3 URL: "s3://bucket/key"
            String key = htmlS3Url.substring(htmlS3Url.lastIndexOf("/") + 1);
            if (htmlS3Url.startsWith("s3://")) {
                // Extract key from s3://bucket/key
                int bucketEnd = htmlS3Url.indexOf("/", 5); // Skip "s3://"
                if (bucketEnd > 0) {
                    key = htmlS3Url.substring(bucketEnd + 1);
                }
            }

            File outputFile = new File(outputFilePath);

            // Create parent directories if they don't exist
            if (outputFile.getParentFile() != null && !outputFile.getParentFile().exists()) {
                outputFile.getParentFile().mkdirs();
            }

            s3.getObject(GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .build(),
                    outputFile.toPath());
            Logger.getLogger().log("HTML file downloaded from S3: " + key + " -> " + outputFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Logger.getLogger().log("Error downloading HTML file from S3: " + e.getMessage());
            return false;
        }
    }

    /**
     * Upload file to S3
     * @param file File to upload
     * @return S3 URL of the uploaded file
     */
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
