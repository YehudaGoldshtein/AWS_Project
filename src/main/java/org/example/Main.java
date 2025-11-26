package org.example;
public class Main {
    public static void main(String[] args) {
        Logger.getLogger().log("Starting Client Application");
        System.out.println("Starting Client Application, logger is: " + Logger.getLogger());
        ClientApp.run(args);
    }

}