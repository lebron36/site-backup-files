import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class Main {
    // Ваши источники плейлистов
    private static final String[] SOURCES = {
        "https://smolnp.github.io/IPTVru/IPTVru.m3u",
        "https://iptv-org.github.io/iptv/languages/rus.m3u",
        "https://raw.githubusercontent.com/naggdd/iptv/refs/heads/main/ru.m3u",
        "https://raw.githubusercontent.com/Bogdannix/iptv/refs/heads/main/Boiptv.m3u",
        "https://raw.githubusercontent.com/UtMax/KazRusIPTV/refs/heads/main/KazRusIPTV.m3u8",
        "https://raw.githubusercontent.com/smolnp/IPTVru/refs/heads/gh-pages/IPTVmir.m3u8",
        "https://raw.githubusercontent.com/Projects-Untitled/iptv-ru/refs/heads/main/index.m3u"
    };

    // Класс для хранения данных канала
    static class Channel {
        String name;
        String logo;
        String url;

        Channel(String name, String logo, String url) {
            this.name = name;
            this.logo = logo;
            this.url = url;
        }
    }

    public static void main(String[] args) {
        // Map для дедупликации (Ключ - название канала, значение - объект Channel)
        Map<String, Channel> channelMap = new LinkedHashMap<>();

        for (String source : SOURCES) {
            System.out.println("Загрузка источника: " + source);
            parseM3U(source, channelMap);
        }

        System.out.println("Всего найдено уникальных каналов: " + channelMap.size());
        System.out.println("Начинаю проверку ссылок на работоспособность...");

        List<String> finalPlaylist = new ArrayList<>();
        finalPlaylist.add("#EXTM3U");

        int count = 0;
        for (Channel ch : channelMap.values()) {
            if (isLinkWorking(ch.url)) {
                String info = "#EXTINF:-1 tvg-logo=\"" + ch.logo + "\"," + ch.name;
                finalPlaylist.add(info);
                finalPlaylist.add(ch.url);
                count++;
                if (count % 10 == 0) System.out.println("Проверено рабочих: " + count);
            }
        }

        try {
            Files.write(Paths.get("playlist.m3u"), finalPlaylist);
            System.out.println("Готово! Рабочих каналов сохранено: " + count);
        } catch (IOException e) {
            System.err.println("Ошибка записи файла: " + e.getMessage());
        }
    }

    private static void parseM3U(String urlAddress, Map<String, Channel> map) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(urlAddress).openStream()))) {
            String line;
            String currentInfo = null;
            
            // Регулярные выражения для логотипа и названия
            Pattern logoPattern = Pattern.compile("tvg-logo=\"(.*?)\"");
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#EXTINF")) {
                    currentInfo = line;
                } else if (line.startsWith("http") && currentInfo != null) {
                    // Извлекаем название канала (все после последней запятой в строке #EXTINF)
                    String name = currentInfo.substring(currentInfo.lastIndexOf(",") + 1).trim();
                    
                    // Извлекаем логотип
                    Matcher matcher = logoPattern.matcher(currentInfo);
                    String logo = matcher.find() ? matcher.group(1) : "";

                    // Если канала еще нет в базе, добавляем его
                    if (!map.containsKey(name)) {
                        map.put(name, new Channel(name, logo, line));
                    }
                    currentInfo = null;
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка при чтении " + urlAddress + ": " + e.getMessage());
        }
    }

    private static boolean isLinkWorking(String urlString) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            connection.setConnectTimeout(3000); // 3 секунды на ожидание
            connection.setReadTimeout(3000);
            int responseCode = connection.getResponseCode();
            return (responseCode == 200);
        } catch (Exception e) {
            return false;
        }
    }
}
