import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class Main {
    // Имена файлов должны в точности совпадать с теми, что в yml
    private static final String OUTPUT_FILE = "assets.bin";
    private static final String INDEX_FILE = "index.html";

    private static final String[] SOURCES = {
        "https://smolnp.github.io/IPTVru/IPTVru.m3u",
        "https://iptv-org.github.io/iptv/languages/rus.m3u",
        "https://m3u.su/lgs",
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
        System.out.println("--- Запуск парсинга источников ---");
        Map<String, Channel> channelMap = new ConcurrentHashMap<>();

        // 1. Сбор данных (параллельно из всех URL)
        Arrays.stream(SOURCES).parallel().forEach(source -> parseM3U(source, channelMap));
        System.out.println("Найдено уникальных каналов: " + channelMap.size());

        // 2. Быстрая проверка ссылок (многопоточно)
        List<String> finalLines = Collections.synchronizedList(new ArrayList<>());
        finalLines.add("#EXTM3U");

        System.out.println("Проверка работоспособности ссылок...");
        channelMap.values().parallelStream().forEach(ch -> {
            if (isLinkWorking(ch.url)) {
                finalLines.add("#EXTINF:-1 tvg-logo=\"" + ch.logo + "\"," + ch.name);
                finalLines.add(ch.url);
            }
        });

        // 3. Запись файлов
        try {
            // Сохраняем плейлист
            Files.write(Paths.get(OUTPUT_FILE), finalLines);
            System.out.println("Файл " + OUTPUT_FILE + " успешно обновлен.");

            // Создаем index.html (чтобы GitHub Actions не выдавал ошибку 128)
            String htmlContent = "<html><body style='font-family:sans-serif;text-align:center;padding-top:50px;'>"
                               + "<h1>System Status: Online</h1>"
                               + "<p>Assets updated: " + new Date() + "</p>"
                               + "</body></html>";
            Files.write(Paths.get(INDEX_FILE), Collections.singletonList(htmlContent));
            System.out.println("Файл " + INDEX_FILE + " успешно создан.");

        } catch (IOException e) {
            System.err.println("Ошибка при записи файлов: " + e.getMessage());
        }
    }

    private static void parseM3U(String urlStr, Map<String, Channel> map) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new URL(urlStr).openStream()))) {
            String line, info = null;
            Pattern logoPattern = Pattern.compile("tvg-logo=\"(.*?)\"");
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#EXTINF")) {
                    info = line;
                } else if (line.startsWith("http") && info != null) {
                    String name = info.substring(info.lastIndexOf(",") + 1).trim();
                    Matcher m = logoPattern.matcher(info);
                    String logo = m.find() ? m.group(1) : "";
                    map.putIfAbsent(name, new Channel(name, logo, line));
                    info = null;
                }
            }
        } catch (Exception e) {
            System.err.println("Не удалось прочитать источник: " + urlStr);
        }
    }

    private static boolean isLinkWorking(String urlStr) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setRequestMethod("GET"); 
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            c.setConnectTimeout(3000); // 3 секунды на ожидание
            c.setReadTimeout(3000);
            int code = c.getResponseCode();
            return (code >= 200 && code < 400); // Считаем рабочими все коды 2xx и 3xx
        } catch (Exception e) {
            return false;
        }
    }
}
