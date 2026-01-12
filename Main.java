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

    // Твои 8 проверенных источников
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
        String name, logo, url, group, tvgId;
        Channel(String name, String logo, String url, String group, String tvgId) {
            this.name = name; this.logo = logo; this.url = url; 
            this.group = group; this.tvgId = tvgId;
        }
    }

    public static void main(String[] args) {
        System.out.println("--- Старт обновления медиа-ресурсов ---");
        Map<String, Channel> channelMap = new ConcurrentHashMap<>();

        // 1. Сбор данных из всех источников параллельно
        Arrays.stream(SOURCES).parallel().forEach(source -> parseM3U(source, channelMap));
        System.out.println("Всего найдено уникальных каналов: " + channelMap.size());

        // 2. Многопоточная проверка ссылок на работоспособность
        System.out.println("Начинаю проверку ссылок...");
        List<Channel> activeChannels = channelMap.values().parallelStream()
                .filter(ch -> isLinkWorking(ch.url))
                .collect(Collectors.toList());

        // 3. Сортировка по алфавиту (игнорируя регистр)
        activeChannels.sort(Comparator.comparing(ch -> ch.name.toLowerCase()));

        // 4. Формирование контента плейлиста
        List<String> finalLines = new ArrayList<>();
        // Заголовок с поддержкой программы передач (EPG)
        finalLines.add("#EXTM3U url-tvg=\"http://itv.xyz/epg/epg.xml.gz\"");

        for (Channel ch : activeChannels) {
            // Формат строки с поддержкой EPG, логотипов и групп
            String extInf = String.format("#EXTINF:-1 tvg-id=\"%s\" tvg-name=\"%s\" tvg-logo=\"%s\" group-title=\"%s\",%s",
                            ch.tvgId, ch.name, ch.logo, ch.group, ch.name);
            finalLines.add(extInf);
            finalLines.add(ch.url);
        }

        // 5. Запись итоговых файлов
        try {
            Files.write(Paths.get(OUTPUT_FILE), finalLines);
            
            // Создание страницы-заглушки для маскировки домена
            String statusHtml = "<html><body style='font-family:sans-serif;text-align:center;margin-top:100px;'>"
                              + "<h1>System Status: Online</h1>"
                              + "<p>Last Update: " + new Date() + "</p>"
                              + "</body></html>";
            Files.write(Paths.get(INDEX_FILE), Collections.singletonList(statusHtml));
            
            System.out.println("Успешно! Рабочих каналов: " + activeChannels.size());
            System.out.println("Файл сохранен как: " + OUTPUT_FILE);
        } catch (IOException e) {
            System.err.println("Ошибка при сохранении: " + e.getMessage());
        }
    }

    private static void parseM3U(String urlStr, Map<String, Channel> map) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new URL(urlStr).openStream()))) {
            String line, info = null;
            Pattern logoPat = Pattern.compile("tvg-logo=\"(.*?)\"");
            Pattern groupPat = Pattern.compile("(?:group-title|group)=\"(.*?)\"");
            Pattern idPat = Pattern.compile("tvg-id=\"(.*?)\"");

            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#EXTINF")) {
                    info
