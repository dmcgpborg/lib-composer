package ru.gpb;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    static String BASE_URL;
    static String AUTH_HEADER;
    static String LOCAL_MAVEN_REPO;
    static String LOCAL_GRADLE_REPO;

    static Properties properties = new Properties();

    public static void main(String[] args) throws Exception {
        init();
        List<String> params = Arrays.asList(args);
        if (params.contains("listMaven")) {
            listMavenLibs();
        }
        if (params.contains("listGradle")) {
            listGradleLibs();
        }
        if (params.contains("printMissing")) {
            printMissingLibs();
        }
    }

    private static void init() throws IOException {
        properties.load(Main.class.getClassLoader().getResourceAsStream("settings.properties"));
        byte[] encodedAuth = Base64.getEncoder().encode(properties.getProperty("auth").getBytes(StandardCharsets.UTF_8));
        AUTH_HEADER = "Basic " + new String(encodedAuth);
        BASE_URL = properties.getProperty("nexus.repo.url");
        LOCAL_MAVEN_REPO = properties.getProperty("local.maven.repo");
        LOCAL_GRADLE_REPO = properties.getProperty("local.gradle.repo");
    }

    private static void printMissingLibs() throws Exception {
        Set<String> NEEDED = new HashSet<>(Files.readAllLines(Paths.get(Main.class.getClassLoader().getResource("needed.txt").toURI())));
        Set<String> EXCLUDE = new HashSet<>();//new HashSet<>(Files.readAllLines(Paths.get(Main.class.getClassLoader().getResource("exclude.txt").toURI())));
        File dir = new File(LOCAL_MAVEN_REPO);
        filterByLocalRepo(dir, "", NEEDED, null);
        for (String needed : NEEDED.stream().sorted().toList()) {
            if (EXCLUDE.contains(needed)) {
                continue;
            }
            if (!isExistInNexus(needed)) {
                System.out.println(needed);
            }
        }
    }

    public static void filterByLocalRepo(final File folder, String subDir, Set<String> NEEDED, Set<String> DIRS) {
        if (DIRS == null) {
            DIRS = new HashSet<>(1000);
        }
        String artifact = null;
        for (final File fileEntry : folder.listFiles()) {
            if (fileEntry.isDirectory()) {
                filterByLocalRepo(fileEntry, subDir + "\\" + fileEntry.getName(), NEEDED, DIRS);
            } else {
                if (fileEntry.getName().contains("lastUpdated")) {
                    return;
                }
                if (!DIRS.contains(subDir)) {
                    DIRS.add(subDir);
                    List<String> strings = Arrays.asList(subDir.split("\\\\"));
                    artifact = String.join(".", strings.subList(1, strings.size() - 2)) + ":"
                            + strings.get(strings.size() - 2) + ":" + strings.get(strings.size() - 1);
                }
            }
        }
        if (artifact != null) {
            NEEDED.remove(artifact);
        }
    }

    public static boolean isExistInNexus(String artifact) throws Exception {
        String[] split = artifact.split(":");
        URL url = new URL(String.format("%s%s/%s/%s/", BASE_URL, split[0].replace(".", "/"),
                split[1], split[2]));
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestProperty("Authorization", AUTH_HEADER);
        con.setRequestMethod("GET");
        int responseCode = con.getResponseCode();
        con.disconnect();
        return responseCode == 200;
    }

    public static void listMavenLibs() {
        File dir = new File(LOCAL_MAVEN_REPO);
        listFilesForFolder(dir, "", null);
    }

    public static void listFilesForFolder(final File folder, String subDir, Set<String> DIRS) {
        if (DIRS == null) {
            DIRS = new HashSet<>(1000);
        }
        for (final File fileEntry : folder.listFiles()) {
            if (fileEntry.isDirectory()) {
                listFilesForFolder(fileEntry, subDir + "\\" + fileEntry.getName(), DIRS);
            } else {
                if (!DIRS.contains(subDir)) {
                    DIRS.add(subDir);
                    List<String> strings = Arrays.asList(subDir.split("\\\\"));
                    System.out.println(String.join(".", strings.subList(1, strings.size() - 2)) + ":"
                            + strings.get(strings.size() - 2) + ":" + strings.get(strings.size() - 1));
                }
            }
        }
    }

    public static void listGradleLibs() {
        File dir = new File(LOCAL_GRADLE_REPO);
        for (final File group : dir.listFiles()) {
            if (group.isDirectory()) {
                for (File artifact : group.listFiles()) {
                    if (artifact.isDirectory()) {
                        for (File version : artifact.listFiles()) {
                            System.out.println(String.format("%s:%s:%s",
                                    group.getName(), artifact.getName(), version.getName()));
                        }
                    }
                }
            }
        }
    }
}