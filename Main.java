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
        "https://raw.githubusercontent.com/Husan1405/m3u/refs/heads/main/TeletochkaTVbetablocks.m3u",
        "https://voxlist.short.gy/m3u",
        "https://raw.githubusercontent.com/Spirt007/Tvru/refs/heads/Master/Rus.m3u",
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

    public static void main(String[] args) throws InterruptedException {
        // Используем ConcurrentHashMap для безопасной записи из разных потоков
        Map<String, Channel> channelMap = new ConcurrentHashMap<>();
        // Создаем пул на 50 одновременных потоков
        ExecutorService executor = Executors.newFixedThreadPool(50);

        // 1. Сбор всех ссылок из источников
        for (String source : SOURCES) {
            parseM3U(source, channelMap, false, executor);
        }

        // 2. Сбор из локального файла Erotika.m3u
        parseM3U(LOCAL_ADULT_SOURCE, channelMap, true, executor);

        // Остановка приема новых задач и ожидание завершения проверок
        executor.shutdown();
        if (!executor.awaitTermination(20, TimeUnit.MINUTES)) {
            executor.shutdownNow();
        }

        // 3. Формирование финального списка (только те, что прошли проверку и попали в map)
        List<Channel> activeChannels = new ArrayList<>(channelMap.values());

        // 4. Сортировка по алфавиту
        activeChannels.sort(Comparator.comparing(ch -> ch.name.toLowerCase()));
