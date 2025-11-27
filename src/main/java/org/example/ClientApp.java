package org.example;
import software.amazon.awssdk.services.sqs.model.Message;
import java.io.File;
import java.util.List;
import java.util.Map;

import static org.example.ManagerService.LOCAL_TO_MANAGER_REQUEST_QUEUE;
import static org.example.ManagerService.MANAGER_TO_LOCAL_REQUEST_QUEUE;


public class ClientApp {

    // All queues used in the system
    private static final String[] ALL_QUEUES = {
        LOCAL_TO_MANAGER_REQUEST_QUEUE,      // LocalToManagerRequestQueue
        MANAGER_TO_LOCAL_REQUEST_QUEUE,      // ManagerToLocalRequestQueue
        "WorkerToManagerRequestQueue",
        "ManagerToWorkerRequestQueue",
        "ManagerRequestQueue",
        "WorkerRequestQueue",
        "LogToLocalQueue"
    };

    public static void run(String[] args){
        //process terminal args
        Map<TerminalParams, String> terminalParamsMap = parseArgs(args);

        // Clean up all queues before starting
        cleanupAllQueues();
        if (terminalParamsMap == null){
            System.out.println("Invalid arguments. Usage: <file_path> <analysis_type> <file_cap> [terminate]");
            return;
        }
        String inputFilePath = terminalParamsMap.get(TerminalParams.FILE_PATH);
        String outputFilePath = terminalParamsMap.get(TerminalParams.OUTPUT_PATH);
        String nParam = terminalParamsMap.get(TerminalParams.FILE_CAP);
        TerminateAction terminateParam = getTerminalParam(terminalParamsMap);

        // Parse n parameter
        int n = 5; // default
        if (nParam != null && !nParam.isEmpty()) {
            try {
                n = Integer.parseInt(nParam);
            } catch (NumberFormatException e) {
                Logger.getLogger().log("Invalid n parameter, using default: 5");
            }
        }

        File inputFile = new File(inputFilePath);
        if (!inputFile.exists()){
            Logger.getLogger().log("File does not exist: " + inputFilePath + " in path " + inputFile.getAbsolutePath());
            return;
        }

        //manager existence verification is done in the MangerService class
        if (ManagerService.getManager(true) == null){
            Logger.getLogger().log("Manager setup failed. Exiting.");
            return;
        }

        //upload file to S3
        String s3Url = S3Service.uploadFile(inputFile);

        //handle upload failure
        if (s3Url == null){
            Logger.getLogger().log("File upload to S3 failed for file: " + inputFile.getName());
            finish(terminateParam);
            return;
        }

        // Send message format: "S3_URL" or "S3_URL;n"
        String messageToManager = s3Url + ";" + n;


        // Use the correct queue name (LocalToManagerRequestQueue)
        SqsService.sendMessage(LOCAL_TO_MANAGER_REQUEST_QUEUE, messageToManager);
        Logger.getLogger().log("File sent to manager: " + inputFile.getName() + " (n=" + n + ")");

        //check every second if the result file is in S3 by looking for a "Done" message in the SQS
        while (!done()){

            List<Message> messages = SqsService.getMessagesForQueue(MANAGER_TO_LOCAL_REQUEST_QUEUE);
            if (!messages.isEmpty()){
                for (Message message : messages) {
                    Logger.getLogger().log("Received message: " + message.body());
                    String messageBody = message.body();

                    // Check for DONE message: "DONE;INPUT_FILE_S3_URL;HTML_S3_URL"
                    if (messageBody.startsWith("DONE;")) {
                        String[] parts = messageBody.split(";");
                        if (parts.length >= 3 && parts[1].equals(s3Url)) {
                            Logger.getLogger().log("Analysis complete for file: " + inputFile.getName());
                            String htmlS3Url = parts[2];

                            // Download HTML file from S3 and save to output file
                            boolean success = S3Service.downloadHTMLFile(htmlS3Url, outputFilePath);
                            if (success) {
                                Logger.getLogger().log("HTML file downloaded and saved to: " + outputFilePath);
                            } else {
                                Logger.getLogger().log("Failed to download HTML file from: " + htmlS3Url);
                            }

                            //delete the message
                            SqsService.deleteMessage(MANAGER_TO_LOCAL_REQUEST_QUEUE, message);
                            finish(terminateParam);
                            return;
                        }
                    }
                    // Check for ERROR message: "ERROR;INPUT_FILE_S3_URL;ERROR_MESSAGE"
                    else if (messageBody.startsWith("ERROR;")) {
                        String[] parts = messageBody.split(";");
                        if (parts.length >= 2 && parts[1].equals(s3Url)) {
                            Logger.getLogger().log("Analysis error for file: " + inputFile.getName());
                            if (parts.length >= 3) {
                                Logger.getLogger().log("Error message: " + parts[2]);
                            }
                            //delete the message
                            SqsService.deleteMessage(MANAGER_TO_LOCAL_REQUEST_QUEUE, message);
                            finish(terminateParam);
                            return;
                        }
                    }
                }
            }

            // Small sleep to avoid busy waiting
            try {
                Thread.sleep(1000); // 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private static Map<TerminalParams, String> parseArgs(String[] args){
        Map<TerminalParams, String> params = new java.util.HashMap<>();
        System.out.println("Parsing args...");
        System.out.println("Arg 0 (input): " + args[0]);
        params.put(TerminalParams.FILE_PATH, args[0]);
        System.out.println("Arg 1 (output): " + args[1]);
        params.put(TerminalParams.OUTPUT_PATH, args[1]);
        System.out.println("Arg 2 (n): " + args[2]);
        params.put(TerminalParams.FILE_CAP, args[2]);
        if (args == null || args.length < 3 || args.length > 4){
            return null;
        }
        if (args.length == 4){
            System.out.println("Arg 3 (terminate): " + args[3]);
            params.put(TerminalParams.TERMINATE, args[3]);
        }
        return params;
    }

    private static void finish(TerminateAction action){
        Logger.getLogger().log("ClientApp finished execution.");
        switch (action)
        {
            case NOTHING:
                //do nothing
                break;
            case TERMINATE:
                ManagerService.terminateManager();
                break;
        }
    }

    private static TerminateAction getTerminalParam(Map<TerminalParams, String> terminalParamsMap){
        if (terminalParamsMap.containsKey(TerminalParams.TERMINATE)){
            switch (terminalParamsMap.get(TerminalParams.TERMINATE).toLowerCase()){
                case "terminate":
                    return TerminateAction.TERMINATE;
                default:
                    return TerminateAction.NOTHING;
            }
        }
        return TerminateAction.NOTHING;
    }

    enum TerminalParams {
        FILE_PATH, OUTPUT_PATH, FILE_CAP, TERMINATE
    }

    enum TerminateAction {
        TERMINATE, NOTHING
    }

    static boolean done(){
        //later we can determine if we are done based on some condition like is file done processing
        return false;
    }


    enum taskTypes{
        DONE, ERROR
    }

    private static void cleanupAllQueues() {
        Logger.getLogger().log("Cleaning up all SQS queues before starting...");
        for (String queueName : ALL_QUEUES) {
            try {
                SqsService.cleanUpSQSQueues(queueName);
                Logger.getLogger().log("Purged queue: " + queueName);
            } catch (Exception e) {
                Logger.getLogger().log("Failed to purge queue " + queueName + ": " + e.getMessage());
            }
        }
        Logger.getLogger().log("Queue cleanup complete.");
    }

}
