package net.taskid;

import sun.awt.image.ToolkitImage;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static net.taskid.MenuItemBuilder.checkBox;
import static net.taskid.MenuItemBuilder.item;

public class TrayScriptRunner {

    private static TrayIcon trayIcon;
    private static PopupMenu popupMenu;
    private static Menu scriptMenu;
    private static Menu settingsMenu;

    private static Executor executor;
    private static File scriptFolder;
    private static File lastScript;

    private static File programFolder;
    private static File configFile;
    private static Properties settings;

    private static boolean showOutput = false;
    private static int lettersUntilBigOutput = 120;
    private static int linesUntilBigOutput = 4;

    public static void main(String[] args) throws IOException {
        if (!SystemTray.isSupported()) {
            JOptionPane.showMessageDialog(null, "SystemTray is not supported", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            programFolder = new File(System.getenv("APPDATA") + "/TrayScriptRunner");
        } else {
            programFolder = new File("/home/" + System.getProperty("user.name") + "/.config/TrayScriptRunner");
        }
        if (!programFolder.exists()) {
            programFolder.mkdirs();
        }

        configFile = new File(programFolder, "config.ini");
        settings = new Properties();

        if(!configFile.exists()) {
            settings.setProperty("script_folder", "");
            settings.setProperty("last_script", "");
            settings.setProperty("show_output", "false");
            settings.setProperty("letters_until_big_output", "120");
            settings.setProperty("lines_until_big_output", "4");
            saveSettings();
        } else {
            try (FileInputStream in = new FileInputStream(configFile)) {
                settings.load(in);
            }
            scriptFolder = new File(settings.getProperty("script_folder"));
            lastScript = new File(settings.getProperty("last_script"));
            showOutput = Boolean.parseBoolean(settings.getProperty("show_output"));
            try {
                lettersUntilBigOutput = Integer.parseInt(settings.getProperty("letters_until_big_output"));
                linesUntilBigOutput = Integer.parseInt(settings.getProperty("lines_until_big_output"));
            } catch (Exception e) {
                System.err.println("Can't parse big output bounds, using default values 120 & 4");
                e.printStackTrace();
            }
        }

        PrintStream printStream = new PrintStream(new File(programFolder, "system_output.log"));
        System.setOut(printStream);
        System.setErr(printStream);


        executor = Executors.newSingleThreadExecutor();
        try (InputStream stream = TrayScriptRunner.class.getResourceAsStream("/script.png")) {
            if (stream == null) {
                throw new NullPointerException("Can't find resource script.png");
            }
            trayIcon = new TrayIcon(ImageIO.read(stream), "Run last script again");
        } catch (Exception e) {
            System.err.println("Can't find file script.png in: " + new File(".").getAbsolutePath());
            e.printStackTrace();
            return;
        }
        popupMenu = new PopupMenu();
        scriptMenu = new Menu("Scripts");
        settingsMenu = new Menu("Settings");

        if(lastScript != null && lastScript.exists()) {
            trayIcon.setToolTip("Run " + lastScript.getName());
        }

        popupMenu.add(scriptMenu);
        popupMenu.add(settingsMenu);

        settingsMenu.add(checkBox("Show Output", showOutput).stateChange(e -> {
            showOutput = !showOutput;
            settings.setProperty("show_output", Boolean.toString(showOutput));
            saveSettings();
        }).build());
        settingsMenu.addSeparator();
        settingsMenu.add(item("Select Script Folder").click(e -> {
            JFileChooser chooser = new JFileChooser(scriptFolder);
            chooser.setDialogTitle("Select Scripts Folder");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setAcceptAllFileFilterUsed(false);
            if (chooser.showDialog(null, "Yep, this one!") == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                if (!file.exists() || !file.isDirectory()) {
                    return;
                }
                scriptFolder = file;
                settings.setProperty("script_folder", scriptFolder.getAbsolutePath());
                saveSettings();
                reloadScripts();
            }
        }).build());
        settingsMenu.add(item("Open Script Folder").click(e -> {
            openExplorerFolder(scriptFolder, "There is no script folder selected yet.");
        }).build());
        settingsMenu.addSeparator();
        settingsMenu.add(item("Open Settings Folder").click(e -> openExplorerFolder(programFolder, "Folder does not exist.")).build());
        popupMenu.addSeparator();
        popupMenu.add(item("Exit").click(e -> System.exit(0)).build());

        trayIcon.setPopupMenu(popupMenu);


        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    if(lastScript == null || !lastScript.exists()) {
                        JOptionPane.showMessageDialog(null, "The script doesn't exist anymore or\nno script was run before.\n\nRight click the tray icon\nto configure the program.", "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    runScript(lastScript);
                }
                if (e.getButton() == MouseEvent.BUTTON2) {
                    openExplorerFolder(scriptFolder, "There is no script folder selected yet.");
                }
            }
        });

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            JOptionPane.showMessageDialog(null, "SystemTray Icon couldn't be added", "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }

        reloadScripts();
    }

    private static void openExplorerFolder(File folder, String invalid) {
        if (folder == null || !folder.exists()) {
            JOptionPane.showMessageDialog(null, invalid, "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().open(folder);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Error:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void saveSettings() {
        try(FileWriter writer = new FileWriter(configFile)) {
            settings.store(writer, "");
        } catch (IOException e) {
            System.err.println("Failed to save settings: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void reloadScripts() {
        scriptMenu.removeAll();

        if (scriptFolder == null) {
            scriptMenu.addSeparator();
            scriptMenu.add(item("Reload").click(e -> TrayScriptRunner.reloadScripts()).build());
            return;
        }
        File[] files = scriptFolder.listFiles((dir, name) -> name.endsWith(".cmd") || name.endsWith(".bat") || name.endsWith(".sh"));
        if (files == null || files.length == 0) {
            scriptMenu.addSeparator();
            scriptMenu.add(item("Reload").click(e -> TrayScriptRunner.reloadScripts()).build());
            return;
        }

        for (File f : files) {
            scriptMenu.add(item(f.getName()).click(e -> {
                executor.execute(() -> runScript(f));
            }).build());
        }
        scriptMenu.addSeparator();
        scriptMenu.add(item("Reload").click(e -> TrayScriptRunner.reloadScripts()).build());
    }

    private static void runScript(File f) {
        System.out.println("Running script " + f.getAbsolutePath() + "...");
        setImage("flash.png");
        try {
            ProcessBuilder builder = new ProcessBuilder(f.getAbsolutePath());
            if (f.getParentFile() != null && f.getParentFile().isDirectory()) {
                builder.directory(f.getParentFile());
            }
            Process process = builder.start();
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            StringBuilder output = new StringBuilder();
            int lines = 0;

            String s = null;
            while ((s = stdInput.readLine()) != null) {
                System.out.println(s.trim());
                if (showOutput) {
                    output.append(s.trim()).append("\n");
                    lines++;
                }
            }

            while ((s = stdError.readLine()) != null) {
                System.err.println(s.trim());
                if (showOutput) {
                    output.append(s.trim()).append("\n");
                    lines++;
                }
            }

            setImage("script.png");
            if (showOutput) {
                String text = output.toString();
                if (text.length() >= lettersUntilBigOutput || lines > linesUntilBigOutput) {
                    JTextArea textArea = new JTextArea(text);
                    textArea.setAutoscrolls(true);
                    textArea.setEditable(false);
                    JScrollPane scrollPane = new JScrollPane(textArea);
                    textArea.setWrapStyleWord(true);
                    scrollPane.setPreferredSize(new Dimension(500, 400));
                    JOptionPane.showMessageDialog(null, scrollPane, "Output (" + f.getName() + ")", JOptionPane.PLAIN_MESSAGE);
                } else {
                    trayIcon.displayMessage(f.getName(), text, TrayIcon.MessageType.NONE);
//                    JOptionPane.showMessageDialog(null, text, "Output (" + f.getName() + ")", JOptionPane.PLAIN_MESSAGE);
                }
            }

            System.out.println("Done.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, "Error while trying to execute:\n" + f.getAbsolutePath() + "\n\nLogs: " + programFolder.getAbsolutePath(), "Error", JOptionPane.ERROR_MESSAGE);
            System.err.println("Exit with error: " + ex.getMessage());
            ex.printStackTrace();
            setImage("script.png");
        }
        if(lastScript == null || !lastScript.equals(f)) {
            lastScript = f;
            trayIcon.setToolTip("Run " + f.getName());
            settings.setProperty("last_script", f.getAbsolutePath());
            saveSettings();
        }
    }

    private static void setImage(String name) {
        try (InputStream stream = TrayScriptRunner.class.getResourceAsStream("/" + name)) {
            if (stream == null) {
                throw new NullPointerException("Can't find resource " + name);
            }
            trayIcon.setImage(ImageIO.read(stream));
        } catch (IOException e) {
            System.err.println("Couldn't set trayIcon image: " + e.getMessage());
            e.printStackTrace();
        }
    }

}

