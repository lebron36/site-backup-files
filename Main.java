import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class Main {
    private static final String OUTPUT_FILE = "content.bin"; // Название-маскировка
    private static final String[] SOURCES = {
        "https://smolnp.github.io/IPTVru/IPTVru.m3u",
        "https://iptv-org.github.io/iptv/languages/rus.m3u",
        "https://raw.githubusercontent.com/naggdd/iptv/refs/heads/main/ru.m3u",
        "https://raw.githubusercontent.com/Bogdannix/iptv/refs/heads/main/Boiptv.m3u",
        "https://raw.githubusercontent.com/UtMax/KazRusIPTV/refs/heads/main/KazRusIPTV.m3u8",
        "https://raw.githubusercontent.com/smolnp/IPTVru/refs/heads/gh-pages/IPTVmir.m3u8",
        "https://raw.githubusercontent.com/Projects-Untitled/iptv-ru/refs/heads/main/index.m3u"
    };

    static class Channel {
        String name, logo, url;
        Channel(String name, String logo, String url) {
            this.name = name; this.logo = logo; this.url = url;
        }
    }

    public static void main(String[] args) {
        Map<String, Channel> channelMap = new ConcurrentHashMap<>();

        // 1. Сбор данных из всех источников
        Arrays.stream(SOURCES).parallel().forEach(source -> parseM3U(source, channelMap));
        System.out.println("Найдено уникальных кандидатов: " + channelMap.size());

        // 2. Быстрая проверка ссылок в многопоточном режиме
        List<String> finalLines = Collections.synchronizedList(new ArrayList<>());
        finalLines.add("#EXTM3U");

        System.out.println("Начинаю проверку работоспособности...");
        channelMap.values().parallelStream().forEach(ch -> {
            if (isLinkWorking(ch.url)) {
                finalLines.add("#EXTINF:-1 tvg-logo=\"" + ch.logo + "\"," + ch.name);
                finalLines.add(ch.url);
            }
        });

        // 3. Сохранение результата и маскировочного файла
        try {
            Files.write(Paths.get(OUTPUT_FILE), finalLines);
            Files.write(Paths.get("assets.bin"), 
                Collections.singletonList("<html><body></body></html>"));
            System.out.println("Успешно! Рабочих каналов: " + (finalLines.size() / 2));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void parseM3U(String urlStr, Map<String, Channel> map) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new URL(urlStr).openStream()))) {
            String line, info = null;
            Pattern p = Pattern.compile("tvg-logo=\"(.*?)\"");
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#EXTINF")) info = line;
                else if (line.startsWith("http") && info != null) {
                    String name = info.substring(info.lastIndexOf(",") + 1).trim();
                    Matcher m = p.matcher(info);
                    String logo = m.find() ? m.group(1) : "";
                    map.putIfAbsent(name, new Channel(name, logo, line));
                    info = null;
                }
            }
        } catch (Exception ignored) {}
    }

    private static boolean isLinkWorking(String urlStr) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setRequestMethod("GET"); // Некоторые серверы не любят HEAD
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            c.setConnectTimeout(3000);
            c.setReadTimeout(3000);
            return (c.getResponseCode() == 200);
        } catch (Exception e) { return false; }
    }
}
