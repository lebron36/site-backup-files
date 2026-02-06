import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.stream.Collectors;

public class Main {
    private static final String OUTPUT_FILE = "assets.txt";
    private static final String INDEX_FILE = "index.html";
    private static final String LOCAL_ADULT_SOURCE = "Erotika.m3u"; 

    private static final String[] SOURCES = {
        "https://raw.githubusercontent.com/777nightman777/FreeIpTV/refs/heads/main/CloudFlare%20Inc.m3u",
        "https://raw.githubusercontent.com/777nightman777/FreeIpTV/refs/heads/main/PeersTV.m3u",
        "https://raw.githubusercontent.com/Spirt007/Tvru/refs/heads/Master/Rus.m3u"
    };

    static class Channel {
        String name, logo, url, group, tvgId;
        Channel(String name, String logo, String url, String group, String tvgId) {
            this.name = name; this.logo = logo; this.url = url; 
            this.group = group; this.tvgId = tvgId;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Map<String, Channel> channelMap = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(50);

        for (String source : SOURCES) {
            parseM3U(source, channelMap, false, executor);
        }

        parseM3U(LOCAL_ADULT_SOURCE, channelMap, true, executor);

        executor.shutdown();
        if (!executor.awaitTermination(20, TimeUnit.MINUTES)) {
            executor.shutdownNow();
        }

        List<Channel> activeChannels = new ArrayList<>(channelMap.values());
        activeChannels.sort(Comparator.comparing(ch -> ch.name.toLowerCase()));

        List<String> finalLines = new ArrayList<>();
        finalLines.add("#EXTM3U url-tvg=\"http://itv.xyz/epg/epg.xml.gz\"");

        for (Channel ch : activeChannels) {
            String extInf = String.format("#EXTINF:-1 tvg-id=\"%s\" tvg-name=\"%s\" tvg-logo=\"%s\" group-title=\"%s\",%s",
                            ch.tvgId, ch.name, ch.logo, ch.group, ch.name);
            finalLines.add(extInf);
            finalLines.add(ch.url);
        }

        try {
            Files.write(Paths.get(OUTPUT_FILE), finalLines);
            Files.write(Paths.get(INDEX_FILE), Collections.singletonList("<html><body>Online</body></html>"));
            System.out.println("Успешно обновлено! Рабочих каналов: " + activeChannels.size());
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static void parseM3U(String source, Map<String, Channel> map, boolean isAdultFile, ExecutorService executor) {
        try {
            InputStream is;
            if (source.startsWith("http")) {
                is = new URL(source).openStream();
            } else {
                File localFile = new File(source);
                if (!localFile.exists()) return;
                is = new FileInputStream(localFile);
            }

            try (BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
                String line, info = null;
                Pattern logoPat = Pattern.compile("tvg-logo=\"(.*?)\"");
                Pattern groupPat = Pattern.compile("(?:group-title|group)=\"(.*?)\"");

                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("#EXTINF")) {
                        info = line;
                    } else if (line.startsWith("http") && info != null) {
                        final String currentUrl = line;
                        final String currentInfo = info;
                        
                        executor.submit(() -> {
                            if (isLinkWorking(currentUrl)) {
                                String name = currentInfo.substring(currentInfo.lastIndexOf(",") + 1).trim();
                                Matcher mLogo = logoPat.matcher(currentInfo);
                                String logo = mLogo.find() ? mLogo.group(1) : "";

                                String group;
                                if (isAdultFile) {
                                    group = "XXX 18+";
                                } else {
                                    Matcher mGroup = groupPat.matcher(currentInfo);
                                    group = mGroup.find() ? mGroup.group(1) : "Общие";
                                }
                                map.putIfAbsent(name, new Channel(name, logo, currentUrl, group, name));
                            }
                        });
                        info = null;
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static boolean isLinkWorking(String urlStr) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setRequestMethod("GET");
            c.setRequestProperty("User-Agent", "VLC/3.0.18");
            c.setConnectTimeout(5000); 
            c.setReadTimeout(5000);
            int code = c.getResponseCode();
            return (code >= 200 && code < 400);
        } catch (Exception e) { return false; }
    }
} // ВАЖНО: ПРОВЕРЬ, ЧТОБЫ ЭТА СКОБКА БЫЛА В САМОМ КОНЦЕ
