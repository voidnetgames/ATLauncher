/*
 * ATLauncher - https://github.com/ATLauncher/ATLauncher
 * Copyright (C) 2013-2022 ATLauncher
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.atlauncher;

import java.awt.Dialog.ModalityType;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;

import org.mini2Dx.gettext.GetText;

import com.atlauncher.builders.HTMLBuilder;
import com.atlauncher.constants.Constants;
import com.atlauncher.data.DownloadableFile;
import com.atlauncher.data.Instance;
import com.atlauncher.data.LauncherVersion;
import com.atlauncher.gui.dialogs.ProgressDialog;
import com.atlauncher.gui.tabs.PacksBrowserTab;
import com.atlauncher.managers.AccountManager;
import com.atlauncher.managers.ConfigManager;
import com.atlauncher.managers.CurseForgeUpdateManager;
import com.atlauncher.managers.DialogManager;
import com.atlauncher.managers.FTBUpdateManager;
import com.atlauncher.managers.InstanceManager;
import com.atlauncher.managers.LWJGLManager;
import com.atlauncher.managers.LogManager;
import com.atlauncher.managers.MinecraftManager;
import com.atlauncher.managers.ModrinthModpackUpdateManager;
import com.atlauncher.managers.NewsManager;
import com.atlauncher.managers.PackManager;
import com.atlauncher.managers.PerformanceManager;
import com.atlauncher.managers.ServerManager;
import com.atlauncher.managers.TechnicModpackUpdateManager;
import com.atlauncher.network.DownloadPool;
import com.atlauncher.network.NetworkClient;
import com.atlauncher.utils.Java;
import com.atlauncher.utils.OS;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import okhttp3.OkHttpClient;

public class Launcher {
    // Holding update data
    private LauncherVersion latestLauncherVersion; // Latest Launcher version
    private List<DownloadableFile> launcherFiles; // Files the Launcher needs to download

    // UI things
    private JFrame parent; // Parent JFrame of the actual Launcher
    private PacksBrowserTab packsBrowserPanel; // The packs browser panel

    // Update thread
    private Thread updateThread;

    // Minecraft tracking variables
    private Process minecraftProcess = null; // The process minecraft is running on
    public boolean minecraftLaunched = false; // If Minecraft has been Launched
    public Instance lastInstanceCrash = null; // The last instance that crashed
    public Date lastInstanceCrashTime = null; // The time the last instance crashed

    public void loadEverything() {
        PerformanceManager.start();

        ConfigManager.loadConfig(); // Load the config

        NewsManager.loadNews(); // Load the news

        MinecraftManager.loadMinecraftVersions(); // Load info about the different Minecraft versions
        MinecraftManager.loadJavaRuntimes(); // Load info about the different java runtimes
        LWJGLManager.loadLWJGLVersions(); // Load info about the different LWJGL versions

        AccountManager.loadAccounts(); // Load the saved Accounts

        PackManager.loadPacks(); // Load the Packs available in the Launcher

        PackManager.loadUsers(); // Load the Testers and Allowed Players for the packs

        InstanceManager.loadInstances(); // Load the users installed Instances

        ServerManager.loadServers(); // Load the users installed servers

        PackManager.removeUnusedImages(); // remove unused pack images

        if (OS.isWindows() && !Java.is64Bit() && OS.is64Bit()) {
            LogManager.warn("You're using 32 bit Java on a 64 bit Windows install!");

            int ret = DialogManager.yesNoDialog().setTitle(GetText.tr("Running 32 Bit Java on 64 Bit Windows"))
                .setContent(new HTMLBuilder().center().text(GetText.tr(
                        "We have detected that you're running 64 bit Windows but not 64 bit Java.<br/><br/>This will cause severe issues playing all packs if not fixed.<br/><br/>Do you want to close the launcher and learn how to fix this issue now?"))
                    .build())
                .setType(DialogManager.ERROR).show();

            if (ret == 0) {
                OS.openWebBrowser("https://atlauncher.com/help/32bit/");
                System.exit(0);
            }
        }

        checkForExternalPackUpdates();

        System.gc();
        PerformanceManager.end();
    }

    public boolean launcherHasUpdate() {
        try (InputStreamReader fileReader = new InputStreamReader(
            Files.newInputStream(FileSystem.JSON.resolve("version.json")), StandardCharsets.UTF_8)) {
            this.latestLauncherVersion = Gsons.DEFAULT.fromJson(fileReader, LauncherVersion.class);
        } catch (JsonSyntaxException | JsonIOException | IOException e) {
            LogManager.logStackTrace("Exception when loading latest launcher version!", e);
        }

        return this.latestLauncherVersion != null && Constants.VERSION.needsUpdate(this.latestLauncherVersion);
    }

    public void downloadUpdate() {
        try {
            File thisFile = new File(Update.class.getProtectionDomain().getCodeSource().getLocation().getPath());
            String path = thisFile.getCanonicalPath();
            path = URLDecoder.decode(path, "UTF-8");
            String toget;
            String saveAs = thisFile.getName();
            if (path.contains(".exe")) {
                toget = "exe";
            } else {
                toget = "jar";
            }
            File newFile = FileSystem.TEMP.resolve(saveAs).toFile();
            LogManager.info("Downloading Launcher Update");

            ProgressDialog<Boolean> progressDialog = new ProgressDialog<>(GetText.tr("Downloading Launcher Update"), 1,
                GetText.tr("Downloading Launcher Update"));
            progressDialog.addThread(new Thread(() -> {
                com.atlauncher.network.Download download = com.atlauncher.network.Download.build()
                    .setUrl(String.format("%s/%s.%s", Constants.DOWNLOAD_SERVER, Constants.LAUNCHER_NAME, toget))
                    .withHttpClient(Network.createProgressClient(progressDialog)).downloadTo(newFile.toPath());

                progressDialog.setTotalBytes(download.getFilesize());

                try {
                    download.downloadFile();
                } catch (IOException e) {
                    LogManager.logStackTrace("Failed to download update", e);
                    progressDialog.setReturnValue(false);
                    progressDialog.close();
                    return;
                }

                progressDialog.setReturnValue(true);
                progressDialog.doneTask();
                progressDialog.close();
            }));
            progressDialog.start();

            if (Boolean.TRUE.equals(progressDialog.getReturnValue())) {
                runUpdate(path, newFile.getAbsolutePath());
            }
        } catch (IOException e) {
            LogManager.logStackTrace(e);
        }
    }

    public void runUpdate(String currentPath, String temporaryUpdatePath) {
        List<String> arguments = new ArrayList<>();

        String path = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        if (OS.isWindows()) {
            path += "w";
        }
        arguments.add(path);
        arguments.add("-cp");
        arguments.add(temporaryUpdatePath);
        arguments.add("com.atlauncher.Update");
        arguments.add(currentPath);
        arguments.add(temporaryUpdatePath);

        // pass in all the original arguments
        arguments.addAll(Arrays.asList(App.PASSED_ARGS));

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(arguments);

        LogManager.info("Running launcher update with command " + arguments);

        try {
            processBuilder.start();
        } catch (IOException e) {
            LogManager.logStackTrace(e);
        }

        System.exit(0);
    }

    /**
     * This checks the servers files.json file and gets the files that the Launcher needs to have
     */
    private List<com.atlauncher.network.Download> getLauncherFiles() {
        if (this.launcherFiles == null) {
            java.lang.reflect.Type type = new TypeToken<List<DownloadableFile>>() {
            }.getType();

            try {
                this.launcherFiles = NetworkClient.get(
                    String.format("%s/launcher/json/files.json", Constants.DOWNLOAD_SERVER),
                    type);
            } catch (Exception e) {
                LogManager.logStackTrace("Error loading in file hashes!", e);
                return null;
            }
        }

        if (this.launcherFiles == null) {
            return null;
        }

        return this.launcherFiles.stream()
            .filter(file -> !file.isLauncher() && !file.isFiles() && file.isForArchAndOs())
            .map(DownloadableFile::getDownload).collect(Collectors.toList());
    }

    public void downloadUpdatedFiles() {
        ProgressDialog<Object> progressDialog = new ProgressDialog<>(GetText.tr("Downloading Updates"), 1,
            GetText.tr("Downloading Updates"));
        progressDialog.addThread(new Thread(() -> {
            DownloadPool pool = new DownloadPool();
            OkHttpClient httpClient = Network.createProgressClient(progressDialog);
            pool.addAll(
                getLauncherFiles().stream().map(dl -> dl.withHttpClient(httpClient)).collect(Collectors.toList()));
            DownloadPool smallPool = pool.downsize();

            progressDialog.setTotalBytes(smallPool.totalSize());

            pool.downloadAll();
            progressDialog.doneTask();
            progressDialog.close();
        }));
        progressDialog.start();

        LogManager.info("Finished downloading updated files!");
    }

    public boolean checkForUpdatedFiles() {
        this.launcherFiles = null;

        App.TASKPOOL.execute(this::checkForExternalPackUpdates);

        return hasUpdatedFiles();
    }

    /**
     * This checks the servers hashes.json file and looks for new/updated files that differ from what the user has
     */
    public boolean hasUpdatedFiles() {
        LogManager.info("Checking for updated files!");
        List<com.atlauncher.network.Download> downloads = getLauncherFiles();

        if (downloads == null) {
            return false;
        }

        return downloads.stream().anyMatch(com.atlauncher.network.Download::needToDownload);
    }

    public void checkForExternalPackUpdates() {
        if (updateThread != null && updateThread.isAlive()) {
            updateThread.interrupt();
        }

        updateThread = new Thread(() -> {
            if (InstanceManager.getInstances().stream().anyMatch(Instance::isFTBPack)) {
                FTBUpdateManager.checkForUpdates();
            }
            if (InstanceManager.getInstances().stream().anyMatch(Instance::isCurseForgePack)) {
                CurseForgeUpdateManager.checkForUpdates();
            }
            if (InstanceManager.getInstances().stream().anyMatch(Instance::isTechnicPack)) {
                TechnicModpackUpdateManager.checkForUpdates();
            }
            if (InstanceManager.getInstances().stream().anyMatch(Instance::isModrinthPack)) {
                ModrinthModpackUpdateManager.checkForUpdates();
            }
        });
        updateThread.start();
    }

    public void updateData() {
        updateData(false);
    }

    public void updateData(boolean force) {
        if (checkForUpdatedFiles()) {
            reloadLauncherData();
        }

        MinecraftManager.loadMinecraftVersions(); // Load info about the different Minecraft versions
        MinecraftManager.loadJavaRuntimes(); // Load info about the different java runtimes
    }

    public void reloadLauncherData() {
        final JDialog dialog = new JDialog(this.parent, ModalityType.DOCUMENT_MODAL);
        dialog.setSize(300, 100);
        dialog.setTitle("Updating Launcher");
        dialog.setLocationRelativeTo(App.launcher.getParent());
        dialog.setLayout(new FlowLayout());
        dialog.setResizable(false);
        dialog.add(new JLabel(GetText.tr("Updating Launcher. Please Wait")));
        App.TASKPOOL.execute(() -> {
            checkForExternalPackUpdates();

            ConfigManager.loadConfig(); // Load the config
            NewsManager.loadNews(); // Load the news
            PackManager.loadPacks(); // Load the Packs available in the Launcher
            reloadPacksBrowserPanel();// Reload packs browser panel
            PackManager.loadUsers(); // Load the Testers and Allowed Players for the packs
            InstanceManager.loadInstances(); // Load the users installed Instances
            dialog.setVisible(false); // Remove the dialog
            dialog.dispose(); // Dispose the dialog
        });
        dialog.setVisible(true);
    }

    private void checkForLauncherUpdate() {
        PerformanceManager.start();

        LogManager.debug("Checking for launcher update");
        if (launcherHasUpdate()) {
            if (App.noLauncherUpdate) {
                int ret = DialogManager.okDialog().setTitle("Launcher Update Available")
                    .setContent(new HTMLBuilder().center().split(80).text(GetText.tr(
                            "An update to the launcher is available. Please update via your package manager or manually by visiting https://atlauncher.com/downloads to get the latest features and bug fixes."))
                        .build())
                    .addOption(GetText.tr("Visit Downloads Page")).setType(DialogManager.INFO).show();

                if (ret == 1) {
                    OS.openWebBrowser("https://atlauncher.com/downloads");
                }

                return;
            }

            if (!App.wasUpdated) {
                downloadUpdate(); // Update the Launcher
            } else {
                DialogManager.okDialog().setTitle("Update Failed!")
                    .setContent(new HTMLBuilder().center()
                        .text(GetText.tr("Update failed. Please click Ok to close "
                            + "the launcher and open up the downloads page.<br/><br/>Download "
                            + "the update and replace the old exe/jar file."))
                        .build())
                    .setType(DialogManager.ERROR).show();
                OS.openWebBrowser("https://atlauncher.com/downloads");
                System.exit(0);
            }
        }
        LogManager.debug("Finished checking for launcher update");
        PerformanceManager.end();
    }

    /**
     * Sets the main parent JFrame reference for the Launcher
     *
     * @param parent The Launcher main JFrame
     */
    public void setParentFrame(JFrame parent) {
        this.parent = parent;
    }

    public void setMinecraftLaunched(boolean launched) {
        this.minecraftLaunched = launched;
        if (App.TRAY_MENU != null) {
            App.TRAY_MENU.setMinecraftLaunched(launched);
        }
    }

    /**
     * Returns the JFrame reference of the main Launcher
     *
     * @return Main JFrame of the Launcher
     */
    public Window getParent() {
        return this.parent;
    }

    /**
     * Sets the panel used for the Packs Browser
     *
     * @param packsBrowserPanel Packs Browser Panel
     */
    public void setPacksBrowserPanel(PacksBrowserTab packsBrowserPanel) {
        this.packsBrowserPanel = packsBrowserPanel;
    }

    /**
     * Reloads the panel used for the Packs browser
     */
    public void reloadPacksBrowserPanel() {
        this.packsBrowserPanel.reload(); // Reload the packs browser panel
    }

    /**
     * Refreshes the panel used for thePacks browser
     */
    public void refreshPacksBrowserPanel() {
        this.packsBrowserPanel.refresh(); // Refresh the packs browser panel
    }

    public void showKillMinecraft(Process minecraft) {
        this.minecraftProcess = minecraft;
        this.lastInstanceCrash = null;
        this.lastInstanceCrashTime = null;
        App.console.showKillMinecraft();
    }

    public void hideKillMinecraft() {
        App.console.hideKillMinecraft();
    }

    public void killMinecraft() {
        if (this.minecraftProcess != null) {
            LogManager.error("Killing Minecraft");

            this.minecraftProcess.destroy();
            this.minecraftProcess = null;
        } else {
            LogManager.error("Cannot kill Minecraft as there is no instance open!");
        }
    }

    public void setLastInstanceCrash(Instance instance) {
        this.lastInstanceCrash = instance;
        this.lastInstanceCrashTime = new Date();
    }
}
