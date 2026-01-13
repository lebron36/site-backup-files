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
    
    // ВОТ ТВОЙ НОВЫЙ ИСТОЧНИК (ЛОКАЛЬНЫЙ ФАЙЛ)
    private static final String LOCAL_ADULT_SOURCE = "Erotika.m3u"; 

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
        String name, logo, url, group, tvgId;
        Channel(String name, String logo, String url, String group, String tvgId) {
            this.name = name; this.logo = logo; this.url = url; 
            this.group = group; this.tvgId = tvgId;
        }
    }

    public static void main(String[] args) {
        Map<String, Channel> channelMap = new ConcurrentHashMap<>();

        // 1. Загрузка внешних ссылок
        Arrays.stream(SOURCES).parallel().forEach(source -> parseM3U(source, channelMap, false));

        // 2. ЗАГРУЗКА ТВОЕГО ФАЙЛА (С ПРИНУДИТЕЛЬНОЙ ГРУППОЙ XXX 18+)
        parseM3U(LOCAL_ADULT_SOURCE, channelMap, true);

        // 3. Проверка ссылок (с мягким фильтром для mp4)
        List<Channel> activeChannels = channelMap.values().parallelStream()
                .filter(ch -> isLinkWorking(ch.url))
                .collect(Collectors.toList());

        // 4. Сортировка по алфавиту
        activeChannels.sort(Comparator.comparing(ch -> ch.name.toLowerCase()));

        // 5. Формирование плейлиста
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
            System.out.println("Успешно обновлено! Каналов: " + activeChannels.size());
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static void parseM3U(String source, Map<String, Channel> map, boolean isAdultFile) {
        try {
            InputStream is;
            if (source.startsWith("http")) {
                is = new URL(source).openStream();
            } else {
                // Чтение твоего файла Erotika.m3u из репозитория
                File localFile = new File(source);
                if (!localFile.exists()) return;
                is = new FileInputStream(localFile);
            }

            try (BufferedReader r = new BufferedReader(new InputStreamReader(is))) {
                String line, info = null;
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

                        String group;
                        if (isAdultFile) {
                            group = "XXX 18+"; // Принудительно для твоего файла
                        } else {
                            Matcher mGroup = groupPat.matcher(info);
                            group = mGroup.find() ? mGroup.group(1) : "Общие";
                        }

                        map.putIfAbsent(name, new Channel(name, logo, line, group, name));
                        info = null;
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static boolean isLinkWorking(String urlStr) {
        try {
            // Для прямых ссылок на фильмы (.mp4) делаем проверку быстрее
            HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(2500);
            c.setReadTimeout(2500);
            int code = c.getResponseCode();
            return (code >= 200 && code < 400);
        } catch (Exception e) { return false; }
    }
}
