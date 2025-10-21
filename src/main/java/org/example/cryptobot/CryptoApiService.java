package org.example.cryptobot;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;


public class CryptoApiService
{
    private static final Logger logger = LoggerFactory.getLogger(CryptoApiService.class);

    // Время жизни кэша в миллисекундах (5 минут)
    private static final long CACHE_TTL = TimeUnit.MINUTES.toMillis(5);

    // Кэш для хранения цен валют
    private final Map<String, CachedPrice> priceCache = new ConcurrentHashMap<>();

    // Класс для хранения цены и времени кэширования
    private static class CachedPrice
    {
        private final double price;
        private final long timestamp;

        public CachedPrice(double price)
        {
            this.price = price;
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isExpired()
        {
            return System.currentTimeMillis() - timestamp > CACHE_TTL;
        }

        public double getPrice()
        {
            return price;
        }
    }

    private final OkHttpClient client;

    public CryptoApiService()
    {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(5, TimeUnit.SECONDS);
        builder.readTimeout(5, TimeUnit.SECONDS);
        this.client = builder.build();
    }

    public double getPrice(String currencySymbol) throws IOException
    {
        // Проверяем кэш
        CachedPrice cached = priceCache.get(currencySymbol);
        if (cached != null && !cached.isExpired())
        {
            logger.debug("Используется кэшированная цена для {}", currencySymbol);
            return cached.getPrice();
        }

        // Если кэш устарел или отсутствует, делаем запрос к API
        String url = "https://min-api.cryptocompare.com/data/price?fsym=" + currencySymbol + "&tsyms=USD";
        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute())
        {
            if (response.isSuccessful())
            {
                String responseBody = response.body().string();
                JSONObject json = new JSONObject(responseBody);
                double price = json.getDouble("USD");

                // Сохраняем в кэш
                priceCache.put(currencySymbol, new CachedPrice(price));
                logger.debug("Получена цена для {} из API: ${}", currencySymbol, price);
                return price;
            }
            else
            {
                throw new IOException("API error: " + response.code() + " - " + response.message());
            }
        }
    }

    // Новый метод для проверки существования валюты
    public boolean isCurrencyValid(String symbol) throws IOException
    {
        String url = "https://min-api.cryptocompare.com/data/price?fsym=" + symbol + "&tsyms=USD";
        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute())
        {
            if (response.isSuccessful())
            {
                String responseBody = response.body().string();
                JSONObject json = new JSONObject(responseBody);
                return json.has("USD");
            }
            else
            {
                // Проверяем специфические ошибки API
                String responseBody = response.body().string();
                JSONObject json = new JSONObject(responseBody);
                if (json.has("Response") && "Error".equals(json.getString("Response")) && "Invalid coin".equals(json.getString("Message")))
                {
                    return false;
                }
                // Для других ошибок API
                throw new IOException("API error: " + responseBody);
            }
        }
    }


    // Метод для принудительной очистки кэша
    public void clearCache()
    {
        priceCache.clear();
        logger.info("Кэш цен успешно очищен");
    }

    // Метод для получения количества валют в кэше
    public int getCacheSize()
    {
        return priceCache.size();
    }
    // В CryptoApiService
    public List<HistoricalData> getHistoricalData(String symbol, String timeframe, int limit) throws IOException {
        // Исправляем имя параметра: "day" -> "histoday", "hour" -> "histohour"
        String apiEndpoint;
        switch (timeframe.toLowerCase()) {
            case "day":
                apiEndpoint = "histoday";
                break;
            case "hour":
                apiEndpoint = "histohour";
                break;
            case "minute":
                apiEndpoint = "histominute";
                break;
            default:
                throw new IllegalArgumentException("Неподдерживаемый временной интервал: " + timeframe);
        }

        String url = "https://min-api.cryptocompare.com/data/v2/" + apiEndpoint +
                "?fsym=" + symbol +
                "&tsym=USD&limit=" + limit;

        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API request failed with code: " + response.code());
            }

            String responseBody = response.body().string();
            JSONObject json = new JSONObject(responseBody);

            // Проверка на наличие ошибки в ответе
            if (json.has("Response") && "Error".equals(json.getString("Response"))) {
                throw new IOException("API error: " + json.getString("Message"));
            }

            // Проверяем, что ответ содержит данные
            if (json.has("Data") && json.getJSONObject("Data").has("Data")) {
                List<HistoricalData> history = new ArrayList<>();
                JSONArray data = json.getJSONObject("Data").getJSONArray("Data");

                for (int i = 0; i < data.length(); i++) {
                    JSONObject item = data.getJSONObject(i);
                    history.add(new HistoricalData(
                            item.getLong("time"),
                            item.getDouble("close")
                    ));
                }
                return history;
            }

            throw new IOException("Unexpected API response structure: " + responseBody);
        }
    }

    // Новый класс для исторических данных
    public static class HistoricalData {
        private final long timestamp;
        private final double price;

        public HistoricalData(long timestamp, double price) {
            this.timestamp = timestamp;
            this.price = price;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public double getPrice() {
            return price;
        }
    }
}

