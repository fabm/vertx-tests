package pt.fabm;

import java.io.File;
import java.text.MessageFormat;

public class AppClient {
    private static final String PATH_SERVER_DISCOVERY_REPLACER = "${discoveryServer}/";
    protected static AppClient INSTANCE;

    public static AppClient getInstance() {
        if (INSTANCE == null){
            throw new IllegalStateException("AppClient it's not initialized yet");
        }
        return INSTANCE;
    }

    public static void init(File discoveryServerPath, String scriptsPath){
        File scriptsPathDir;

        if(scriptsPath == null){
            throw new IllegalStateException("scriptsPath must be filled");
        }

        if(scriptsPath.startsWith(PATH_SERVER_DISCOVERY_REPLACER)){
            scriptsPathDir = new File(discoveryServerPath, scriptsPath.substring(PATH_SERVER_DISCOVERY_REPLACER.length()));
        }else{
            scriptsPathDir = new File(scriptsPath);
        }

        if(!scriptsPathDir.exists()){
            throw new IllegalStateException(MessageFormat.format("Path doesn''t exist {0}", scriptsPathDir.getAbsolutePath()));
        }
        if(!scriptsPathDir.isDirectory()){
            throw new IllegalStateException(MessageFormat.format("It''s not a directory {0}", scriptsPathDir.getAbsolutePath()));
        }
        if(!scriptsPathDir.canRead()){
            throw new IllegalStateException(MessageFormat.format("It''s not possible to read from {0}", scriptsPathDir.getAbsolutePath()));
        }
        AppClient appClient = new AppClient();
        INSTANCE = appClient;
        appClient.scriptsPath = scriptsPathDir;
    }
    private File scriptsPath;

    public File getScriptsPath() {
        return scriptsPath;
    }

    private AppClient(){
        //do nothing
    }
}
