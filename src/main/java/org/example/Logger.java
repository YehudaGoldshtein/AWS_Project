package org.example;

public class Logger {
    static Logger instance;
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
