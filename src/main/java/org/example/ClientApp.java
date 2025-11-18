package org.example;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.example.Main.MANAGER_REQUEST_QUEUE;

public class ClientApp {



    public static void run(String[] args){
        //process terminal args
        Map<TerminalParams, String> terminalParamsMap = parseArgs(args);
        if (terminalParamsMap == null){
            System.out.println("Invalid arguments. Usage: <file_path> <analysis_type> <file_cap> [terminate]");
            return;
        }
        String filePath = terminalParamsMap.get(TerminalParams.FILE_PATH);
        String analysisType = terminalParamsMap.get(TerminalParams.ANALYSIS_TYPE);
        AnalysisTypes analysis = getAnalysisType(analysisType);
        TerminateAction terminateParam = getTerminalParam(terminalParamsMap);

        //handle sad cases
        if (analysis == null){
            System.out.println("Invalid analysis type. Supported types: pos, constituency, dependency");
            return;
        }

        File file = new File(filePath);
        if (!file.exists()){
            Logger.getLogger().log("File does not exist: " + filePath + " in path" + file.getAbsolutePath());
            return;
        }

        //manager existence verification is done in the MangerService class
        if (ManagerService.getManager() == null){
            Logger.getLogger().log("Manager setup failed. Exiting.");
            return;
        }

        //upload file to S3
        String s3Url = S3Service.uploadFile(file);

        //handle upload failure
        if (s3Url == null){
            Logger.getLogger().log("File upload to S3 failed for file: " + file.getName());
            finish(terminateParam);
            return;
        }

        //send message to manager's SQS
        String requestQueueUrl = SqsService.getSQSQueue(MANAGER_REQUEST_QUEUE);
        Logger.getLogger().log("Setup complete. sending file to manager...");
        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(requestQueueUrl)
                .messageBody(analysis.name() + ";" + s3Url)
                .build();

        SqsService.getClient().sendMessage(request);
        Logger.getLogger().log("File sent to manager: " + file.getName());

        //check every second if the result file is in S3 by looking for a "Done" message in the SQS
        while (true){
            ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                    .queueUrl(requestQueueUrl)
                    .maxNumberOfMessages(1)
                    .waitTimeSeconds(1)
                    .build();

            SqsClient sqsClient = SqsService.getClient();
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
                        S3Service.handleCompletion(file.getName(), message);
                        finish(terminateParam);
                        return;
                    }
                    else if (message.body().equals(taskTypes.ERROR + ";" + file.getName())){
                        Logger.getLogger().log("Analysis error for file: " + file.getName());
                        //delete the message
                        //Todo: download error log from S3, reschedule e.t.c.
                        finish(terminateParam);
                        return;
                    }
                }
            }
        }


    }

    private static Map<TerminalParams, String> parseArgs(String[] args){
        if (args == null || args.length < 3 || args.length > 4){
            return null;
        }
        Map<TerminalParams, String> params = new java.util.HashMap<>();
        params.put(TerminalParams.FILE_PATH, args[0]);
        params.put(TerminalParams.ANALYSIS_TYPE, args[1]);
        params.put(TerminalParams.FILE_CAP, args[2]);
        if (args.length == 4){
            params.put(TerminalParams.TERMINATE, args[3]);
        }
        return params;
    }

    private static void finish(TerminateAction action){
        Logger.getLogger().log("ClientApp finished execution.");
        if (action == TerminateAction.TERMINATE)
        {
            //todo: terminate manager
            //ManagerService.terminateManager();
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

    enum AnalysisTypes {
        POS, CONSTITUENCY, DEPENDENCY
    }

    enum TerminalParams {
        FILE_PATH, ANALYSIS_TYPE, FILE_CAP, TERMINATE
    }

    enum TerminateAction {
        TERMINATE, NOTHING
    }
    
    static  AnalysisTypes getAnalysisType(String analysisType){
        switch (analysisType.toLowerCase()){
            case "pos":
                return AnalysisTypes.POS;
            case "constituency":
                return AnalysisTypes.CONSTITUENCY;
            case "dependency":
                return AnalysisTypes.DEPENDENCY;
            default:
                return null;
        }
    }

    enum taskTypes{
        DONE, ERROR
    }

}
