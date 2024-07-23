package me.ryanhamshire.GriefPrevention.logs;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.data.DataStore;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PermissionChangeLogs {
    private static GriefPrevention plugin = GriefPrevention.getInstance();

    public static void logToFile(String logMessage, boolean consoleLog) {
        Date now = new Date();
        SimpleDateFormat time = new SimpleDateFormat("[dd-MMM-yyyy HH:mm:ss] ");
        String logTime = time.format(now);

        try {
            File folderDir = new File(DataStore.dataLayerFolderPath, "Logs");
            File file = new File(folderDir.getAbsolutePath() + "/PermissionChangeLog.yml");

            if (!file.exists()) {
                file.createNewFile();
            }

            FileWriter fw = new FileWriter(file, true);
            PrintWriter pw = new PrintWriter(fw);

            pw.println(logTime + logMessage);
            pw.flush();
            pw.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        if (consoleLog) plugin.getLogger().info(logMessage);
    }
}
