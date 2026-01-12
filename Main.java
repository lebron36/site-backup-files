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

    private static final String[] SOURCES = {
        "https://raw.githubusercontent.com/Projects-Untitled/iptv-ru/refs/heads/main/index.m3u",
        "https://raw.githubusercontent.com/smolnp/IPTVru/refs/heads/gh-pages/IPTVmir.m3u8",
        "https://raw.githubusercontent.com/UtMax/KazRusIPTV/refs/heads/main/KazRusIPTV.m3u8",
        "https://raw.githubusercontent.com/Bogdannix/iptv/refs/heads/main/Boiptv.m3u",
        "https://raw.githubusercontent.com/naggdd/iptv/refs/heads/main/ru.m3u",
        "https://smolnp.github.io/IPTVru/IPTVru.m3u",
        "https://iptv-org.github.io/iptv/languages/rus.m3u"
    };

    static class Channel {
        String name, logo, url, group;
        Channel(String name, String logo, String url, String group) {
            this.name = name; this.logo = logo; this.url = url; this.group = group;
        }
    }

    public static void main(String[] args) {
        Map<String, Channel> channelMap = new ConcurrentHashMap<>();

        // 1. Сбор данных
        Arrays.stream(SOURCES).parallel().forEach(source -> parseM3U(source, channelMap));

        // 2. Проверка и фильтрация
        List<Channel> activeChannels = channelMap.values().parallelStream()
                .filter(ch -> isLinkWorking(ch.url))
                .collect(Collectors.toList());

        // 3. СОРТИРОВКА ПО АЛФАВИТУ
        activeChannels.sort(Comparator.comparing(ch -> ch.name.toLowerCase()));

        // 4. Формирование финальных строк
        List<String> finalLines = new ArrayList<>();
        finalLines.add("#EXTM3U");
        for (Channel ch : activeChannels) {
            // Формируем строку с логотипом и группой
            String extInf = String.format("#EXTINF:-1 tvg-logo=\"%s\" group-title=\"%s\",%s", 
                            ch.logo, ch.group, ch.name);
            finalLines.add(extInf);
            finalLines.add(ch.url);
        }

        // 5. Запись
        try {
            Files.write(Paths.get(OUTPUT_FILE), finalLines);
            Files.write(Paths.get(INDEX_FILE), Collections.singletonList("<html><body>System Online</body></html>"));
            System.out.println("Готово! Каналов сохранено: " + activeChannels.size());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void parseM3U(String urlStr, Map<String, Channel> map) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new URL(urlStr).openStream()))) {
            String line, info = null;
            // Паттерны для поиска логотипа и группы
            Pattern logoPat = Pattern.compile("tvg-logo=\"(.*?)\"");
            Pattern groupPat = Pattern.compile("(?:group-title|group)=\"(.*?)\"");

            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#EXTINF")) {
                    info = line;
                } else if (line.startsWith("http") && info != null) {
                    String name = info.substring(info.lastIndexOf(",") + 1).trim();
                    
                    Matcher mLogo = logoPat.matcher(info);
                    String logo = mLogo.find() ? mLogo.group(1) : "";

                    Matcher mGroup = groupPat.matcher(info);
                    String group = mGroup.find() ? mGroup.group(1) : "Разное"; // Группа по умолчанию

                    map.putIfAbsent(name, new Channel(name, logo, line, group));
                    info = null;
                }
            }
        } catch (Exception ignored) {}
    }

    private static boolean isLinkWorking(String urlStr) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(3000);
            c.setReadTimeout(3000);
            return (c.getResponseCode() == 200);
        } catch (Exception e) { return false; }
    }
}
