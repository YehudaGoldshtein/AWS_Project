package org.example;
import software.amazon.awssdk.services.sqs.model.Message;
import java.io.File;
import java.util.List;
import java.util.Map;

import static org.example.ManagerService.LOCAL_TO_MANAGER_REQUEST_QUEUE;
import static org.example.ManagerService.MANAGER_TO_LOCAL_REQUEST_QUEUE;

public class ClientApp {



    public static void run(String[] args){
        //process terminal args
        Map<TerminalParams, String> terminalParamsMap = parseArgs(args);
        if (terminalParamsMap == null){
            System.out.println("Invalid arguments. Usage: <file_path> <analysis_type> <file_cap> [terminate]");
            return;
        }
        String filePath = terminalParamsMap.get(TerminalParams.FILE_PATH);
        String outputPath = terminalParamsMap.get(TerminalParams.OUTPUT_PATH);
        TerminateAction terminateParam = getTerminalParam(terminalParamsMap);

        //handle sad cases
        if (outputPath == null || outputPath.isEmpty()){
            System.out.println("Invalid output path.");
            return;
        }

        File file = new File(filePath);
        if (!file.exists()){
            Logger.getLogger().log("File does not exist: " + filePath + " in path" + file.getAbsolutePath());
            return;
        }

        //manager existence verification is done in the MangerService class
        if (ManagerService.getManager(true) == null){
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

        SqsService.cleanUpSQSQueues(LOCAL_TO_MANAGER_REQUEST_QUEUE);
        SqsService.cleanUpSQSQueues(MANAGER_TO_LOCAL_REQUEST_QUEUE);
        SqsService.cleanUpSQSQueues(MANAGER_TO_LOCAL_REQUEST_QUEUE);
        SqsService.cleanUpSQSQueues("WorkerToManagerRequestQueue");

        SqsService.sendMessage(LOCAL_TO_MANAGER_REQUEST_QUEUE, s3Url);
        Logger.getLogger().log("File sent to manager: " + file.getName());

        //check every second if the result file is in S3 by looking for a "Done" message in the SQS
        while (!done()){

            List<Message> messages = SqsService.getMessagesForQueue(MANAGER_TO_LOCAL_REQUEST_QUEUE);
            if (!messages.isEmpty()){
                for (Message message : messages) {
                    Logger.getLogger().log("Received message: " + message.body());
                    if (message.body().equals(taskTypes.DONE + ";" + file.getName())){
                        Logger.getLogger().log("Analysis complete for file: " + file.getName());
                        //delete the message
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
                    SqsService.deleteMessage(MANAGER_TO_LOCAL_REQUEST_QUEUE, message);

                }
            }
        }


    }

    private static Map<TerminalParams, String> parseArgs(String[] args){
        if (args == null || args.length < 4 || args.length > 5){
            return null;
        }
        Map<TerminalParams, String> params = new java.util.HashMap<>();
        System.out.println("Parsing args...");
        System.out.println("Arg 0: " + args[1]);
        params.put(TerminalParams.FILE_PATH, args[1]);
        System.out.println("Arg 1: " + args[2]);
        params.put(TerminalParams.OUTPUT_PATH, args[2]);
        params.put(TerminalParams.FILE_CAP, args[3]);
        if (args.length == 5){
            params.put(TerminalParams.TERMINATE, args[4]);
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
        FILE_PATH, OUTPUT_PATH, FILE_CAP, TERMINATE
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

    static boolean done(){
        //later we can determine if we are done based on some condition like is file done processing
        return false;
    }


    enum taskTypes{
        DONE, ERROR
    }

}
