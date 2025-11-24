package org.example;

public class Logger {
    static Logger instance;

    private  Logger(){
        Thread loggerThread = new Thread(()->{
            while (true){
                try {
                    SqsService.getMessagesForQueue(SqsService.LOG_TO_LOCAL).forEach(msg-> {
                        System.out.println(msg.body());
                        SqsService.deleteMessage(SqsService.LOG_TO_LOCAL, msg);
                    });
                } catch (Throwable ignored) {}
            }
        });
        loggerThread.setDaemon(true);
        loggerThread.start();
    }
    public static Logger getLogger(){
        if (instance == null){
            instance = new Logger();
        }
        return instance;
    }
    public void log(String message){
        System.out.println(message);
    }


}
