import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        // Читаем список того, что мы ищем
        List<String> wanted = Files.readAllLines(Paths.get("wanted_channels.txt"))
                .stream().map(String::trim).map(String::toLowerCase).toList();

        // Читаем ссылки на плейлисты
        List<String> urls = Files.readAllLines(Paths.get("sources.txt"))
                .stream().map(String::trim).filter(s -> !s.isEmpty()).toList();

        // Map для хранения: Ключ = URL потока, Значение = Строка с названием (#EXTINF)
        // LinkedHashMap сохранит порядок, а Map уберет дубликаты ссылок
        Map<String, String> resultChannels = new LinkedHashMap<>();

        for (String urlString : urls) {
            System.out.println("Обработка источника: " + urlString);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(urlString).openStream(), StandardCharsets.UTF_8))) {
                String line;
                String lastInfo = null;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("#EXTINF")) {
                        lastInfo = line;
                    } else if (line.startsWith("http") && lastInfo != null) {
                        String streamUrl = line.trim();
                        String finalLastInfo = lastInfo;
                        // Если название канала содержит любое слово из нашего списка "wanted"
                        if (wanted.stream().anyMatch(w -> finalLastInfo.toLowerCase().contains(w))) {
                            resultChannels.putIfAbsent(streamUrl, lastInfo);
                        }
                        lastInfo = null;
                    }
                }
            } catch (Exception e) {
                System.err.println("Ошибка при скачивании " + urlString + ": " + e.getMessage());
            }
        }

        // Записываем результат в новый файл
        try (PrintWriter writer = new PrintWriter("playlist.m3u")) {
            writer.println("#EXTM3U");
            for (Map.Entry<String, String> entry : resultChannels.entrySet()) {
                writer.println(entry.getValue());
                writer.println(entry.getKey());
            }
        }
        System.out.println("Успех! Собрано уникальных каналов: " + resultChannels.size());
    }
}
