package com.olivia.task.photomosaic;

/**
 * Created by Olivia on 7/27/2016.
 */
public class Config {

    public static final String localhost = "192.168.0.103";
    public static final String port = "8765";
    public static final int tileWitdth = 32;
    public static final int tileHeight = 32;
    public static final String serverUrl = "http://" + localhost + ":" + port + "/color/" + tileWitdth + "/" + tileHeight + "/";
}
