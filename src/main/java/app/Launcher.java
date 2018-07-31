package app;

import gui.WebServer;

import static spark.Spark.*;

public class Launcher {

    public static String configFile;

    public static void main(String[] args) {
        configFile = "config.yml";
        if (args.length > 0)
            configFile = args[0];
        port(8080);
        staticFiles.location("/public");
        init();
        new WebServer();
    }
}