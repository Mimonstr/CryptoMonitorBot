package org.example.cryptobot;

import com.google.gson.Gson;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class CryptoBot extends TelegramLongPollingBot
{
    // Перечисление для типов действий
    private enum InputAction
    {
        ADD, REMOVE, PRICE
    }

    private static final String NOTIFICATION_FINISH = "finish_notifications";
    private static final String NOTIFICATION_COMMAND = "notifications";
    private static final String NOTIFICATION_SETTING_PREFIX = "notify:";
    private static final String NOTIFICATION_INTERVAL_PREFIX = "interval:";
    private static final String NOTIFICATION_CANCEL = "cancel_notifications";
    private static final String REMOVE_NOTIFICATION_PREFIX = "remove_notification:";
    private static final String REMOVE_NOTIFICATION_BUTTON = "❌ Удалить уведомление";

    // Настройки уведомлений (в минутах)
    private static final Map<String, Integer> NOTIFICATION_INTERVALS = new HashMap<>();

    static
    {
        NOTIFICATION_INTERVALS.put("30m", 30);
        NOTIFICATION_INTERVALS.put("2h", 120);
        NOTIFICATION_INTERVALS.put("6h", 360);
        NOTIFICATION_INTERVALS.put("12h", 720);
        NOTIFICATION_INTERVALS.put("24h", 1440);
    }

    private static final String CUSTOM_INTERVAL_COMMAND = "custom_interval";
    private static final String WAITING_FOR_CUSTOM_INTERVAL = "waiting_for_custom_interval";
    private static final int MIN_NOTIFICATION_INTERVAL = 5; // Минимальный интервал в минутах

    // Карта для хранения текущих настроек уведомлений
    private final Map<Long, String> notificationSettings = new HashMap<>();


    // В начало класса
    private static final String CURRENCY_PREFIX = "currency:";
    private static final String REMOVE_CURRENCY_PREFIX = "remove_currency:";
    private static final String PRICE_CURRENCY_PREFIX = "price_currency:";
    private static final String DONE_COMMAND = "done";
    private static final String REMOVE_DONE_COMMAND = "remove_done";
    private static final String PRICE_DONE_COMMAND = "price_done";
    private static final String MANUAL_COMMAND = "manual";
    private static final String PRICE_MANUAL_COMMAND = "price_manual";

    // Список популярных валют
    private static final List<String> POPULAR_CURRENCIES = Arrays.asList(
            "BTC", "ETH", "XRP", "LTC", "ADA", "DOGE", "SOL", "DOT", "SHIB", "MATIC",
            "AVAX", "BNB", "LINK", "UNI", "XMR"
    );

    // Карта для хранения состояний пользователей
    private final Map<Long, String> userStates = new HashMap<>();
    private final DatabaseHelper dbHelper = new DatabaseHelper();
    private final CryptoApiService apiService = new CryptoApiService();
    // Карта для хранения выбранных валют во время мультивыбора
    private final Map<Long, Set<String>> selectedCurrencies = new HashMap<>();
    // Карта для хранения выбранных валют во время мультивыбора для удаления
    private final Map<Long, Set<String>> removalSelectedCurrencies = new HashMap<>();
    // Карта для хранения выбранных валют во время мультивыбора для проверки курса
    private final Map<Long, Set<String>> priceSelectedCurrencies = new HashMap<>();
    public static final Logger logger = LoggerFactory.getLogger(CryptoBot.class);

    public CryptoBot()
    {
        dbHelper.initDatabase();
        startNotificationScheduler();
    }

    private void startNotificationScheduler()
    {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() ->
        {
            try
            {
                checkNotifications();
            }
            catch (Exception e)
            {
                logger.error("Ошибка при проверке уведомлений", e);
            }
        }, 5, 5, TimeUnit.MINUTES);
    }

    @Override
    public void onUpdateReceived(Update update)
    {
        if (update.hasCallbackQuery())
        {
            handleCallbackQuery(update);
        }
        else if (update.hasMessage() && update.getMessage().hasText())
        {
            handleTextMessage(update);
        }
    }

    private void sendMainMenu(long chatId)
    {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Создаем кнопки с эмодзи
        InlineKeyboardButton addButton = new InlineKeyboardButton();
        addButton.setText("🆕 Добавить валюту");
        addButton.setCallbackData("add");

        InlineKeyboardButton removeButton = new InlineKeyboardButton();
        removeButton.setText("❌ Удалить валюту");
        removeButton.setCallbackData("remove");

        InlineKeyboardButton listButton = new InlineKeyboardButton();
        listButton.setText("📋 Список");
        listButton.setCallbackData("list");

        InlineKeyboardButton priceButton = new InlineKeyboardButton();
        priceButton.setText("💰 Проверить курс");
        priceButton.setCallbackData("price");

        InlineKeyboardButton notificationsButton = new InlineKeyboardButton();
        notificationsButton.setText("🔔 Уведомления");
        notificationsButton.setCallbackData(NOTIFICATION_COMMAND);

        // Добавляем каждую кнопку в отдельную строку (вертикальный столбик)
        rows.add(Collections.singletonList(addButton));
        rows.add(Collections.singletonList(removeButton));
        rows.add(Collections.singletonList(listButton));
        rows.add(Collections.singletonList(priceButton));
        rows.add(Collections.singletonList(notificationsButton));

        markup.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Главное меню криптовалютного монитора");
        message.setReplyMarkup(markup);
        try
        {
            execute(message);
        }
        catch (TelegramApiException e)
        {
            e.printStackTrace();
        }
    }

    private void handleMultiSelect(Update update, String prefix, Map<Long, Set<String>> storage, String messageText, Function<Long, InlineKeyboardMarkup> keyboardGenerator)
    {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        long chatId = callbackQuery.getMessage().getChatId();
        long messageId = callbackQuery.getMessage().getMessageId();
        String data = callbackQuery.getData();

        String currency = data.substring(prefix.length());
        Set<String> selected = storage.get(chatId);
        if (selected == null)
        {
            selected = new HashSet<>();
            storage.put(chatId, selected);
        }

        if (selected.contains(currency))
        {
            selected.remove(currency);
        }
        else
        {
            selected.add(currency);
        }

        try
        {
            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(String.valueOf(chatId));
            editMessage.setMessageId((int) messageId);
            editMessage.setText(messageText);
            editMessage.setReplyMarkup(keyboardGenerator.apply(chatId));
            execute(editMessage);
        }
        catch (TelegramApiException e)
        {
            e.printStackTrace();
        }
    }

    private void handleAddDone(long chatId, long messageId)
    {
        Set<String> currencies = selectedCurrencies.get(chatId);
        if (currencies == null || currencies.isEmpty())
        {
            sendConfirmation(chatId, messageId, "⚠️ Нет выбранных валют для добавления");
            return;
        }

        List<String> addedCurrencies = new ArrayList<>();
        List<String> alreadyExistCurrencies = new ArrayList<>();

        for (String currency : currencies)
        {
            if (!dbHelper.isCurrencyFavorite(chatId, currency))
            {
                dbHelper.addCurrency(chatId, currency);
                addedCurrencies.add(currency);
            }
            else
            {
                alreadyExistCurrencies.add(currency);
            }
        }

        sendMultiActionResponse(chatId, messageId, addedCurrencies, alreadyExistCurrencies,
                "✅ Валюта %s успешно добавлена в избранное!\n",
                "✅ Успешно добавлено %d валют: %s\n",
                "⚠️ Валюта %s уже была в избранном\n",
                "⚠️ %d валют уже были в избранном: %s\n",
                "⚠️ Нет выбранных валют для добавления");

        // Добавляем вызов основного меню после завершения операции
        sendMainMenu(chatId);
    }

    private void handleRemoveDone(long chatId, long messageId)
    {
        Set<String> currencies = removalSelectedCurrencies.get(chatId);
        if (currencies == null || currencies.isEmpty())
        {
            sendConfirmation(chatId, messageId, "⚠️ Нет выбранных валют для удаления");
            return;
        }

        List<String> removedCurrencies = new ArrayList<>();
        List<String> notExistCurrencies = new ArrayList<>();

        for (String currency : currencies)
        {
            if (dbHelper.isCurrencyFavorite(chatId, currency))
            {
                dbHelper.removeCurrency(chatId, currency);
                removedCurrencies.add(currency);
                // Также удаляем настройки уведомлений
                dbHelper.removeNotificationSetting(chatId, currency);
            }
            else
            {
                notExistCurrencies.add(currency);
            }
        }

        sendMultiActionResponse(chatId, messageId, removedCurrencies, notExistCurrencies,
                "❌ Валюта %s удалена из избранного!\n",
                "❌ Успешно удалено %d валют: %s\n",
                "⚠️ Валюта %s не найдена в избранном\n",
                "⚠️ %d валют не найдены в избранном: %s\n",
                "⚠️ Нет выбранных валют для удаления");
        // Добавляем вызов основного меню после завершения операции
        sendMainMenu(chatId);
    }

    private void handlePriceDone(long chatId, long messageId)
    {
        Set<String> currencies = priceSelectedCurrencies.get(chatId);
        if (currencies == null || currencies.isEmpty())
        {
            sendConfirmation(chatId, messageId, "⚠️ Нет выбранных валют для проверки курса");
            return;
        }

        StringBuilder message = new StringBuilder();
        for (String symbol : currencies)
        {
            try
            {
                double price = apiService.getPrice(symbol);
                message.append(symbol).append(": $").append(String.format("%.2f", price)).append("\n");
            }
            catch (Exception e)
            {
                message.append(symbol).append(": ошибка получения курса\n");
            }
        }
        sendConfirmation(chatId, messageId, message.toString());
        // Добавляем вызов основного меню после завершения операции
        sendMainMenu(chatId);
    }

    // Общий метод для формирования ответа с успешными и неуспешными операциями
    private void sendMultiActionResponse(long chatId, long messageId,
                                         List<String> successList, List<String> errorList,
                                         String singleSuccess, String multiSuccess,
                                         String singleError, String multiError,
                                         String noActionMessage)
    {
        StringBuilder message = new StringBuilder();

        if (!successList.isEmpty())
        {
            if (successList.size() == 1)
            {
                message.append(String.format(singleSuccess, successList.get(0)));
            }
            else
            {
                String currencyList = String.join(", ", successList);
                message.append(String.format(multiSuccess, successList.size(), currencyList));
            }
        }

        if (!errorList.isEmpty())
        {
            if (errorList.size() == 1)
            {
                message.append(String.format(singleError, errorList.get(0)));
            }
            else
            {
                String currencyList = String.join(", ", errorList);
                message.append(String.format(multiError, errorList.size(), currencyList));
            }
        }

        if (successList.isEmpty() && errorList.isEmpty())
        {
            message.append(noActionMessage);
        }

        sendConfirmation(chatId, messageId, message.toString());
    }

    private void handleCallbackQuery(Update update)
    {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        long userId = callbackQuery.getFrom().getId();
        String data = callbackQuery.getData();
        long messageId = callbackQuery.getMessage().getMessageId();
        long chatId = callbackQuery.getMessage().getChatId();

        // Обработка главного меню
        if (data.equals("add"))
        {
            sendAddKeyboard(chatId);
            return;
        }
        else if (data.equals("remove"))
        {
            sendRemoveKeyboard(chatId);
            return;
        }
        else if (data.equals("list"))
        {
            handleListCommand(chatId);
            return;
        }
        else if (data.equals("price"))
        {
            sendPriceKeyboard(chatId);
            return;
        }

        // Обработка мультивыбора
        if (userStates.containsKey(chatId) && "waiting_for_multi_add".equals(userStates.get(chatId)))
        {
            if (data.equals(MANUAL_COMMAND))
            {
                userStates.put(userId, "waiting_for_manual_currency");
                sendMessage(chatId, "Пожалуйста, введите символ валюты (например, BTC, ETH)");
                return;
            }
            else if (data.equals(DONE_COMMAND))
            {
                handleAddDone(chatId, messageId);
                userStates.remove(chatId);
                selectedCurrencies.remove(chatId);
                return;
            }
            else if (data.startsWith(CURRENCY_PREFIX))
            {
                handleMultiSelect(update, CURRENCY_PREFIX, selectedCurrencies,
                        "Выберите валюту из списка популярных или введите вручную:",
                        this::generateCurrencyKeyboard);
                return;
            }
        }
        else if (userStates.containsKey(chatId) && "waiting_for_multi_remove".equals(userStates.get(chatId)))
        {
            if (data.equals(REMOVE_DONE_COMMAND))
            {
                handleRemoveDone(chatId, messageId);
                userStates.remove(chatId);
                removalSelectedCurrencies.remove(chatId);
                // Также удаляем настройки уведомлений
                for (String currency : removalSelectedCurrencies.get(chatId))
                {
                    dbHelper.removeNotificationSetting(chatId, currency);
                }
                return;
            }
            else if (data.startsWith(REMOVE_CURRENCY_PREFIX))
            {
                handleMultiSelect(update, REMOVE_CURRENCY_PREFIX, removalSelectedCurrencies,
                        "Выберите валюту для удаления из избранного:",
                        chatId1 -> generateRemoveKeyboard(chatId1, dbHelper.getFavoriteCurrencies(chatId1)));
                return;
            }
        }
        else if (data.equals(PRICE_MANUAL_COMMAND))
        {
            userStates.put(chatId, "waiting_for_manual_price");
            sendMessage(chatId, "Пожалуйста, введите символ валюты для проверки курса");
            return;
        }
        else if (data.equals(PRICE_DONE_COMMAND))
        {
            handlePriceDone(chatId, messageId);
            userStates.remove(chatId);
            priceSelectedCurrencies.remove(chatId);
            return;
        }
        else if (data.startsWith(PRICE_CURRENCY_PREFIX))
        {
            handleMultiSelect(update, PRICE_CURRENCY_PREFIX, priceSelectedCurrencies,
                    "Выберите валюты для проверки курса:",
                    this::generatePriceKeyboard);
            return;
        }
        //Обработка Списка
        else if (data.startsWith("chart:"))
        {
            String currency = data.substring(6);
            chatId = callbackQuery.getMessage().getChatId();
            messageId = callbackQuery.getMessage().getMessageId();

            // Отправляем пустой ответ, чтобы не было ошибки "Query is too old"
            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQuery.getId());
            answer.setText("Загрузка графика для " + currency + "...");
            answer.setShowAlert(false);

            try
            {
                execute(answer);
            }
            catch (TelegramApiException e)
            {
                e.printStackTrace();
            }

            // Отправляем график
            sendCurrencyChart(chatId, currency);

            return;
        }

        // Обработка ручного ввода
        else if (data.equals(MANUAL_COMMAND))
        {
            // Определяем текущее состояние пользователя
            String currentState = userStates.get(chatId);

            // Устанавливаем соответствующий статус в зависимости от текущего режима
            if ("waiting_for_multi_remove".equals(currentState))
            {
                userStates.put(userId, "waiting_for_remove_manual");
                sendMessage(chatId, "Пожалуйста, введите символ валюты для удаления (например, BTC, ETH)");
            }
            else if ("waiting_for_multi_price".equals(currentState))
            {
                userStates.put(userId, "waiting_for_manual_price");
                sendMessage(chatId, "Пожалуйста, введите символ валюты для проверки курса");
            }
            else
            {
                // По умолчанию - режим добавления
                userStates.put(userId, "waiting_for_manual_currency");
                sendMessage(chatId, "Пожалуйста, введите символ валюты (например, BTC, ETH)");
            }
            return;
        }
        else if (data.startsWith(CURRENCY_PREFIX))
        {
            String currency = data.substring(CURRENCY_PREFIX.length());
            try
            {
                if (apiService.isCurrencyValid(currency))
                {
                    dbHelper.addCurrency(chatId, currency);
                    sendConfirmation(chatId, messageId, "✅ Валюта " + currency + " добавлена в избранное!");
                }
                else
                {
                    sendConfirmation(chatId, messageId, "❌ Валюта " + currency + " не найдена. Проверьте правильность символа.");
                }
            }
            catch (IOException e)
            {
                sendConfirmation(chatId, messageId, "⚠️ Ошибка при проверке валюты: " + e.getMessage());
            }
            return;
        }
        else if (data.equals(NOTIFICATION_COMMAND))
        {
            sendNotificationMenu(chatId);
            return;
        }
        else if (data.startsWith(NOTIFICATION_SETTING_PREFIX))
        {
            String currency = data.substring(NOTIFICATION_SETTING_PREFIX.length());
            notificationSettings.put(chatId, currency);
            sendIntervalOptions(chatId);
            return;
        }
        else if (data.startsWith(NOTIFICATION_INTERVAL_PREFIX))
        {
            String intervalKey = data.substring(NOTIFICATION_INTERVAL_PREFIX.length());
            Integer minutes = NOTIFICATION_INTERVALS.get(intervalKey);

            if (minutes != null && notificationSettings.containsKey(chatId))
            {
                String currency = notificationSettings.get(chatId);
                dbHelper.addNotificationSetting(chatId, currency, minutes);

                sendConfirmation(chatId, messageId,
                        "✅ Уведомления для " + currency +
                                " установлены на каждые " + intervalKey);

                notificationSettings.remove(chatId);
                sendNotificationMenu(chatId);
            }
            return;
        }
        else if (data.equals(CUSTOM_INTERVAL_COMMAND))
        {
            String currency = notificationSettings.get(chatId);
            if (currency == null)
            {
                sendConfirmation(chatId, messageId, "❌ Ошибка: не выбрана валюта");
                return;
            }

            // Устанавливаем состояние ожидания ввода
            userStates.put(chatId, WAITING_FOR_CUSTOM_INTERVAL);

            sendMessage(chatId,
                    "Введите интервал уведомлений для " + currency + " (в минутах):\n\n" +
                            "🔹 Требования:\n" +
                            "• Минимальное значение: " + MIN_NOTIFICATION_INTERVAL + " минут\n" +
                            "• Значение должно быть кратно 5\n" +
                            "• Примеры допустимых значений: 5, 10, 15, 20, ...\n\n" +
                            "Введите число:");
            return;
        }
        else if (data.startsWith(REMOVE_NOTIFICATION_PREFIX))
        {
            String currency = data.substring(REMOVE_NOTIFICATION_PREFIX.length());

            if (!dbHelper.isNotificationExists(chatId, currency))
            {
                sendConfirmation(chatId, messageId,
                        "❌ Уведомления для " + currency + " не найдены");
                sendNotificationMenu(chatId);
                return;
            }

            // Удаляем уведомление из базы данных
            dbHelper.removeNotificationSetting(chatId, currency);

            // Отправляем подтверждение
            sendConfirmation(chatId, messageId,
                    "✅ Уведомления для " + currency +
                            " удалены\n\nТеперь вы не будете получать уведомления для этой валюты");

            // Очищаем временные данные
            notificationSettings.remove(chatId);

            // Возвращаемся к меню уведомлений
            sendNotificationMenu(chatId);
            return;
        }
        else if (data.equals(NOTIFICATION_FINISH))
        {
            // Очищаем временные настройки
            notificationSettings.remove(chatId);

            // Возвращаемся в главное меню
            sendMainMenu(chatId);
            return;
        }
        else if (data.equals(NOTIFICATION_CANCEL))
        {
            notificationSettings.remove(chatId);
            sendNotificationMenu(chatId);
            return;
        }
    }

    private void sendNotificationMenu(long chatId)
    {
        List<DatabaseHelper.NotificationSetting> settings = dbHelper.getNotificationSettings(chatId);
        StringBuilder message = new StringBuilder("🔔 Настройки уведомлений:\n\n");

        if (settings.isEmpty())
        {
            message.append("У вас нет активных уведомлений");
        }
        else
        {
            message.append("Текущие настройки:\n");
            for (DatabaseHelper.NotificationSetting setting : settings)
            {
                String interval = getIntervalDisplay(setting.getIntervalMinutes());
                message.append("• ").append(setting.getCurrency())
                        .append(" - каждые ").append(interval).append("\n");
            }
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Список валют для настройки
        List<String> favorites = dbHelper.getFavoriteCurrencies(chatId);
        if (!favorites.isEmpty())
        {
            List<InlineKeyboardButton> row = new ArrayList<>();
            for (String currency : favorites)
            {
                InlineKeyboardButton button = new InlineKeyboardButton();
                button.setText(currency);
                button.setCallbackData(NOTIFICATION_SETTING_PREFIX + currency);
                row.add(button);

                if (row.size() == 3)
                {
                    rows.add(row);
                    row = new ArrayList<>();
                }
            }
            if (!row.isEmpty()) rows.add(row);
        }
        else
        {
            message.append("\n\nСначала добавьте валюты в избранное");
        }

        // Кнопка "Закончить настройку"
        InlineKeyboardButton finishButton = new InlineKeyboardButton();
        finishButton.setText("✅ Закончить настройку");
        finishButton.setCallbackData(NOTIFICATION_FINISH);
        rows.add(Collections.singletonList(finishButton));

        markup.setKeyboard(rows);
        sendMessageWithMarkup(chatId, message.toString(), markup);
    }


    private void sendIntervalOptions(long chatId)
    {
        String currency = notificationSettings.get(chatId);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Опции интервалов
        for (String key : NOTIFICATION_INTERVALS.keySet())
        {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(getIntervalDisplay(key));
            button.setCallbackData(NOTIFICATION_INTERVAL_PREFIX + key);
            rows.add(Collections.singletonList(button));
        }

        // Новая опция: свой интервал
        InlineKeyboardButton customButton = new InlineKeyboardButton();
        customButton.setText("✏️ Свой интервал (мин)");
        customButton.setCallbackData(CUSTOM_INTERVAL_COMMAND);
        rows.add(Collections.singletonList(customButton));

        // Кнопка удаления уведомления
        InlineKeyboardButton removeButton = new InlineKeyboardButton();
        removeButton.setText(REMOVE_NOTIFICATION_BUTTON);
        removeButton.setCallbackData(REMOVE_NOTIFICATION_PREFIX + currency);
        rows.add(Collections.singletonList(removeButton));

        // Кнопка "Назад"
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("🔙 Назад");
        backButton.setCallbackData(NOTIFICATION_CANCEL);
        rows.add(Collections.singletonList(backButton));

        markup.setKeyboard(rows);
        sendMessageWithMarkup(chatId,
                "Выберите интервал уведомлений для " + currency + "\n" +
                        "Минимальный интервал: " + MIN_NOTIFICATION_INTERVAL + " минут\n" +
                        "Все значения должны быть кратны 5 минутам",
                markup);
    }

    private String getIntervalDisplay(String key)
    {
        switch (key)
        {
            case "30m":
                return "30 минут";
            case "2h":
                return "2 часа";
            case "6h":
                return "6 часов";
            case "12h":
                return "12 часов";
            case "24h":
                return "1 день";
            default:
                return key;
        }
    }

    private String getIntervalDisplay(int minutes)
    {
        if (minutes < 60) return minutes + " минут";
        int hours = minutes / 60;
        return hours + (hours == 1 ? " час" : " часов");
    }

    private void sendMessageWithMarkup(long chatId, String text, InlineKeyboardMarkup markup)
    {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setReplyMarkup(markup);
        try
        {
            execute(message);
        }
        catch (TelegramApiException e)
        {
            e.printStackTrace();
        }
    }

    private void sendMultiSelectKeyboard(long chatId, String state, Map<Long, Set<String>> storage,
                                         String messageText, Function<Long, InlineKeyboardMarkup> keyboardGenerator)
    {
        // Очищаем предыдущее состояние и выбранные валюты
        userStates.remove(chatId);
        storage.remove(chatId);

        // Устанавливаем новое состояние
        userStates.put(chatId, state);
        storage.put(chatId, new HashSet<>());

        // Отправляем клавиатуру
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(messageText);
        message.setReplyMarkup(keyboardGenerator.apply(chatId));
        try
        {
            execute(message);
        }
        catch (TelegramApiException e)
        {
            e.printStackTrace();
        }
    }

    private void sendAddKeyboard(long chatId)
    {
        sendMultiSelectKeyboard(chatId, "waiting_for_multi_add", selectedCurrencies,
                "Выберите валюту из списка популярных или введите вручную:",
                this::generateCurrencyKeyboard);
    }

    private void sendPriceKeyboard(long chatId)
    {
        sendMultiSelectKeyboard(chatId, "waiting_for_multi_price", priceSelectedCurrencies,
                "Выберите валюты для проверки курса:",
                this::generatePriceKeyboard);
    }

    private void sendRemoveKeyboard(long chatId)
    {
        List<String> favoriteCurrencies = dbHelper.getFavoriteCurrencies(chatId);

        // Если список пустой, сообщаем об этом и выходим
        if (favoriteCurrencies.isEmpty())
        {
            sendMessage(chatId, "Ваш список избранных валют пуст");
            return;
        }

        // Очищаем предыдущее состояние и выбранные валюты для удаления
        userStates.remove(chatId);
        removalSelectedCurrencies.remove(chatId);

        // Устанавливаем новое состояние
        userStates.put(chatId, "waiting_for_multi_remove");
        removalSelectedCurrencies.put(chatId, new HashSet<>());

        // Отправляем клавиатуру с избранными валютами
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Выберите валюту для удаления из избранного:");
        message.setReplyMarkup(generateRemoveKeyboard(chatId, favoriteCurrencies));
        try
        {
            execute(message);
        }
        catch (TelegramApiException e)
        {
            e.printStackTrace();
        }
    }


    private void handleTextMessage(Update update)
    {
        String messageText = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();

        // Обработка состояний ручного ввода
        if (userStates.containsKey(chatId))
        {
            String state = userStates.get(chatId);
            String currency = messageText.trim().toUpperCase();

            if ("waiting_for_manual_currency".equals(state))
            {
                handleManualInput(chatId, currency, InputAction.ADD);
                userStates.remove(chatId);
                return;
            }
            else if ("waiting_for_remove_manual".equals(state))
            {
                handleManualInput(chatId, currency, InputAction.REMOVE);
                userStates.remove(chatId);
                return;
            }
            else if ("waiting_for_manual_price".equals(state))
            {
                handleManualInput(chatId, currency, InputAction.PRICE);
                userStates.remove(chatId);
                return;
            }
            else if (WAITING_FOR_CUSTOM_INTERVAL.equals(userStates.get(chatId)))
            {
                handleCustomIntervalInput(chatId, messageText);
                return;
            }
        }

        // Обработка команд
        if (messageText.equals("/start"))
        {
            sendMainMenu(chatId);
        }
        else if (messageText.equals("/add"))
        {
            sendAddKeyboard(chatId);
        }
        else if (messageText.startsWith("/price "))
        {
            handlePriceCommand(chatId, messageText);
        }
        else if (messageText.startsWith("/remove "))
        {
            handleRemoveCommand(chatId, messageText);
        }
        else if (messageText.equals("/list"))
        {
            handleListCommand(chatId);
        }
        else
        {
            sendMessage(chatId, "Неизвестная команда. Используйте /start для помощи.");
        }
    }

    private void sendConfirmation(long chatId, long messageId, String text)
    {
        if (messageId == 0)
        {
            sendMessage(chatId, text);
        }
        else
        {
            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(String.valueOf(chatId));
            editMessage.setMessageId((int) messageId);
            editMessage.setText(text);
            try
            {
                execute(editMessage);
            }
            catch (TelegramApiException e)
            {
                e.printStackTrace();
            }
        }
    }

    private InlineKeyboardMarkup generateGenericKeyboard(long chatId, Map<Long, Set<String>> storage,
                                                         String callbackPrefix, String buttonTextSuffix)
    {
        Set<String> selected = storage.get(chatId);
        if (selected == null)
        {
            selected = new HashSet<>();
            storage.put(chatId, selected);
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Добавляем валюты по 3 в ряд
        for (int i = 0; i < POPULAR_CURRENCIES.size(); i++)
        {
            String symbol = POPULAR_CURRENCIES.get(i);
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(symbol + (selected.contains(symbol) ? buttonTextSuffix : ""));
            button.setCallbackData(callbackPrefix + symbol);

            // Добавляем кнопку в текущую строку
            if (rows.isEmpty() || rows.get(rows.size() - 1).size() >= 3)
            {
                rows.add(new ArrayList<>());
            }
            rows.get(rows.size() - 1).add(button);
        }

        // Добавляем общие кнопки
        addCommonButtons(rows, callbackPrefix);

        markup.setKeyboard(rows);
        return markup;
    }

    private void addCommonButtons(List<List<InlineKeyboardButton>> rows, String callbackPrefix)
    {
        // Кнопка "Ввести вручную" (без префикса)
        InlineKeyboardButton manualButton = new InlineKeyboardButton();
        manualButton.setText("Ввести вручную");
        manualButton.setCallbackData("manual");
        rows.add(Collections.singletonList(manualButton));

        // Кнопка "Завершить выбор" (без префикса)
        InlineKeyboardButton doneButton = new InlineKeyboardButton();
        doneButton.setText("Завершить выбор");
        doneButton.setCallbackData("done");
        rows.add(Collections.singletonList(doneButton));
    }

    private InlineKeyboardMarkup generateCurrencyListWithPrices(long chatId)
    {
        List<String> currencies = dbHelper.getFavoriteCurrencies(chatId);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        if (currencies.isEmpty())
        {
            return null;
        }

        for (int i = 0; i < currencies.size(); i++)
        {
            String currency = currencies.get(i);
            String priceText = "N/A";

            try
            {
                double price = apiService.getPrice(currency);
                priceText = String.format("$%.2f", price);
            }
            catch (Exception e)
            {
                logger.error("Ошибка при получении цены для {}", currency, e);
            }

            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(currency + " - " + priceText);
            // Уникальный callback_data для графика
            button.setCallbackData("chart:" + currency);
            row.add(button);

            if (row.size() == 3)
            {
                rows.add(row);
                row = new ArrayList<>();
            }
        }

        if (!row.isEmpty())
        {
            rows.add(row);
        }

        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup generateCurrencyKeyboard(long chatId)
    {
        return generateGenericKeyboard(chatId, selectedCurrencies, "currency:", " ✅");
    }

    private InlineKeyboardMarkup generatePriceKeyboard(long chatId)
    {
        Set<String> selected = priceSelectedCurrencies.get(chatId);
        if (selected == null)
        {
            selected = new HashSet<>();
            priceSelectedCurrencies.put(chatId, selected);
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        // Добавляем популярные валюты по 3 в ряд
        for (String symbol : POPULAR_CURRENCIES)
        {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(symbol + (selected.contains(symbol) ? " ✅" : ""));
            button.setCallbackData("price_currency:" + symbol);
            row.add(button);
            if (row.size() == 3)
            {
                rows.add(row);
                row = new ArrayList<>();
            }
        }
        if (!row.isEmpty())
        {
            rows.add(row);
        }

        // Кнопка "Ввести вручную" (специфично для проверки курса)
        InlineKeyboardButton manualButton = new InlineKeyboardButton();
        manualButton.setText("Ввести вручную");
        manualButton.setCallbackData("price_manual");
        rows.add(Collections.singletonList(manualButton));

        // Кнопка "Завершить выбор" (специфично для проверки курса)
        InlineKeyboardButton doneButton = new InlineKeyboardButton();
        doneButton.setText("Завершить выбор");
        doneButton.setCallbackData("price_done");
        rows.add(Collections.singletonList(doneButton));

        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup generateRemoveKeyboard(long chatId, List<String> currencies)
    {
        List<String> cleanCurrencies = cleanCurrencyList(currencies);
        Set<String> selected = removalSelectedCurrencies.get(chatId);
        if (selected == null)
        {
            selected = new HashSet<>();
            removalSelectedCurrencies.put(chatId, selected);
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Добавляем валюты по 3 в ряд
        for (int i = 0; i < cleanCurrencies.size(); i++)
        {
            String symbol = cleanCurrencies.get(i);
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(symbol + (selected.contains(symbol) ? " ❌" : ""));
            button.setCallbackData("remove_currency:" + symbol);

            if (rows.isEmpty() || rows.get(rows.size() - 1).size() >= 3)
            {
                rows.add(new ArrayList<>());
            }
            rows.get(rows.size() - 1).add(button);
        }

        // Добавляем только кнопку "Завершить выбор" (без кнопки "Ввести вручную")
        InlineKeyboardButton doneButton = new InlineKeyboardButton();
        doneButton.setText("Завершить выбор");
        doneButton.setCallbackData("remove_done");
        rows.add(Collections.singletonList(doneButton));

        markup.setKeyboard(rows);
        return markup;
    }

    // Метод для очистки списка валют от префиксов
    private List<String> cleanCurrencyList(List<String> currencies)
    {
        List<String> cleaned = new ArrayList<>();
        for (String s : currencies)
        {
            int lastColon = s.lastIndexOf(':');
            if (lastColon != -1)
            {
                s = s.substring(lastColon + 1);
            }
            cleaned.add(s);
        }
        return cleaned;
    }

    private void handleManualInput(long chatId, String currency, InputAction action)
    {
        // Приводим валюту к верхнему регистру для корректной проверки
        String normalizedCurrency = currency.toUpperCase();

        try
        {
            switch (action)
            {
                case ADD:
                    // Проверяем, есть ли валюта в избранном
                    if (dbHelper.isCurrencyFavorite(chatId, normalizedCurrency))
                    {
                        sendConfirmation(chatId, 0, "⚠️ Валюта " + normalizedCurrency + " уже находится в избранном!");
                    }
                    else if (apiService.isCurrencyValid(normalizedCurrency))
                    {
                        dbHelper.addCurrency(chatId, normalizedCurrency);
                        sendConfirmation(chatId, 0, "✅ Валюта " + normalizedCurrency + " успешно добавлена в избранное!");
                    }
                    else
                    {
                        sendConfirmation(chatId, 0, "❌ Валюта " + normalizedCurrency + " не найдена. Проверьте правильность символа.");
                    }
                    break;

                case REMOVE:
                    if (dbHelper.isCurrencyFavorite(chatId, normalizedCurrency))
                    {
                        dbHelper.removeCurrency(chatId, normalizedCurrency);
                        sendConfirmation(chatId, 0, "❌ Валюта " + normalizedCurrency + " удалена из избранного!");
                    }
                    else
                    {
                        sendConfirmation(chatId, 0, "⚠️ Валюта " + normalizedCurrency + " не найдена в избранном");
                    }
                    break;

                case PRICE:
                    double price = apiService.getPrice(normalizedCurrency);
                    sendMessage(chatId, "Курс " + normalizedCurrency + ": $" + String.format("%.2f", price));
                    break;
            }
        }
        catch (Exception e)
        {
            switch (action)
            {
                case ADD:
                    sendConfirmation(chatId, 0, "⚠️ Ошибка при проверке валюты: " + e.getMessage());
                    break;
                case REMOVE:
                    sendConfirmation(chatId, 0, "⚠️ Ошибка при удалении валюты: " + e.getMessage());
                    break;
                case PRICE:
                    sendMessage(chatId, "Ошибка: валюта не найдена или API недоступен");
                    break;
            }
        }
        // Добавляем вызов основного меню в конце
        sendMainMenu(chatId);
    }

    private void handleCustomIntervalInput(long chatId, String input)
    {
        try
        {
            int minutes = Integer.parseInt(input.trim());

            // Проверка минимального значения
            if (minutes < MIN_NOTIFICATION_INTERVAL)
            {
                sendMessage(chatId,
                        "❌ Ошибка: интервал не может быть меньше " + MIN_NOTIFICATION_INTERVAL + " минут.\n" +
                                "Попробуйте еще раз.");
                return;
            }

            // Новая проверка: кратность 5
            if (minutes % 5 != 0)
            {
                sendMessage(chatId,
                        "❌ Ошибка: интервал должен быть кратен 5 минутам.\n" +
                                "Допустимые значения: 5, 10, 15, 20, ...\n" +
                                "Попробуйте еще раз.");
                return;
            }

            String currency = notificationSettings.get(chatId);
            if (currency == null)
            {
                sendMessage(chatId, "❌ Ошибка: не выбрана валюта");
                userStates.remove(chatId);
                return;
            }

            dbHelper.addNotificationSetting(chatId, currency, minutes);

            sendMessage(chatId,
                    "✅ Уведомления для " + currency +
                            " установлены на каждые " + minutes + " минут\n\n" +
                            "Следующее уведомление придет через " + minutes + " минут");

            // Очищаем временные данные
            notificationSettings.remove(chatId);
            userStates.remove(chatId);

            // Возвращаемся к меню уведомлений
            sendNotificationMenu(chatId);
        }
        catch (NumberFormatException e)
        {
            sendMessage(chatId,
                    "❌ Ошибка: пожалуйста, введите число (количество минут).\n" +
                            "Значение должно быть кратно 5 (примеры: 5, 10, 15)\n" +
                            "Минимальное значение: " + MIN_NOTIFICATION_INTERVAL + " минут");
        }
    }

    private void sendMessage(long chatId, String text)
    {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        try
        {
            execute(message);
        }
        catch (TelegramApiException e)
        {
            e.printStackTrace();
        }
    }

    // Универсальный обработчик команд с префиксом
    private void handleGenericCommand(long chatId, String messageText, int prefixLength, InputAction action)
    {
        String currency = messageText.substring(prefixLength).trim().toUpperCase();
        handleManualInput(chatId, currency, action);
    }

    private void handlePriceCommand(long chatId, String messageText)
    {
        handleGenericCommand(chatId, messageText, 7, InputAction.PRICE);
    }

    private void handleRemoveCommand(long chatId, String messageText)
    {
        handleGenericCommand(chatId, messageText, 8, InputAction.REMOVE);
    }

    private void handleListCommand(long chatId)
    {
        List<String> currencies = dbHelper.getFavoriteCurrencies(chatId);

        if (currencies.isEmpty())
        {
            sendMessage(chatId, "Ваш список избранных валют пуст");
            sendMainMenu(chatId);
            return;
        }

        try
        {
            InlineKeyboardMarkup markup = generateCurrencyListWithPrices(chatId);

            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText("Ваши избранные валюты с текущими ценами:");
            message.setReplyMarkup(markup);

            execute(message);
            sendMainMenu(chatId); // Отправляем главное меню сразу после списка
        }
        catch (TelegramApiException e)
        {
            e.printStackTrace();
            sendMessage(chatId, "Ошибка при отображении списка: " + e.getMessage());
            sendMainMenu(chatId);
        }
    }

    private void checkNotifications()
    {
        long currentTimeSeconds = System.currentTimeMillis() / 1000; // Текущее время в секундах
        logger.info("Проверка уведомлений. Текущее время: {}", new Date(currentTimeSeconds * 1000));

        try
        {
            // Получаем список всех пользователей с активными уведомлениями
            Set<Long> allUserIds = getAllUserIdsWithNotifications();

            for (Long userId : allUserIds)
            {
                List<DatabaseHelper.NotificationSetting> settings = dbHelper.getNotificationSettings(userId);
                logger.info("Проверка уведомлений для пользователя {}: {} настроек", userId, settings.size());

                for (DatabaseHelper.NotificationSetting setting : settings)
                {
                    try
                    {
                        if (shouldSendNotification(userId, setting, currentTimeSeconds))
                        {
                            logger.info("Отправка уведомления для {} ({}): интервал {} минут",
                                    userId, setting.getCurrency(), setting.getIntervalMinutes());

                            sendNotification(userId, setting);
                            updateLastNotificationTime(userId, setting.getCurrency());
                        }
                    }
                    catch (Exception e)
                    {
                        logger.error("Ошибка при обработке уведомления для пользователя {} и валюты {}",
                                userId, setting.getCurrency(), e);
                    }
                }
            }
        }
        catch (Exception e)
        {
            logger.error("Критическая ошибка при проверке уведомлений", e);
        }
    }

    private Set<Long> getAllUserIdsWithNotifications()
    {
        Set<Long> userIds = new HashSet<>();
        try (Connection conn = DriverManager.getConnection(DatabaseHelper.DB_URL);
             PreparedStatement stmt = conn.prepareStatement("SELECT DISTINCT user_id FROM notifications");
             ResultSet rs = stmt.executeQuery())
        {

            while (rs.next())
            {
                userIds.add(rs.getLong("user_id"));
            }
        }
        catch (SQLException e)
        {
            logger.error("Ошибка при получении списка пользователей с уведомлениями", e);
        }
        return userIds;
    }

    private boolean shouldSendNotification(long userId, DatabaseHelper.NotificationSetting setting, long currentTimeSeconds)
    {
        try (Connection conn = DriverManager.getConnection(DatabaseHelper.DB_URL);
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT last_notification FROM notifications WHERE user_id = ? AND currency = ?"))
        {

            stmt.setLong(1, userId);
            stmt.setString(2, setting.getCurrency());
            ResultSet rs = stmt.executeQuery();

            if (rs.next())
            {
                long lastNotification = rs.getLong("last_notification");
                long intervalSeconds = setting.getIntervalMinutes() * 60L;

                logger.debug("Проверка для {}: текущее время {}, последнее уведомление {}, интервал {} секунд",
                        setting.getCurrency(), currentTimeSeconds, lastNotification, intervalSeconds);

                // Добавляем небольшой запас времени (5 секунд) для надежности
                return (currentTimeSeconds - lastNotification) >= intervalSeconds - 5;
            }
        }
        catch (SQLException e)
        {
            logger.error("Ошибка при проверке необходимости уведомления для {}",
                    setting.getCurrency(), e);
        }
        return false;
    }

    private void updateLastNotificationTime(long chatId, String currency)
    {
        try (PreparedStatement stmt = dbHelper.getConnection().prepareStatement(
                "UPDATE notifications SET last_notification = strftime('%s', 'now') " +
                        "WHERE user_id = ? AND currency = ?"))
        {
            stmt.setLong(1, chatId);
            stmt.setString(2, currency);
            stmt.executeUpdate();
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }
    }

    private void sendNotification(long chatId, DatabaseHelper.NotificationSetting setting)
    {
        try
        {
            double price = apiService.getPrice(setting.getCurrency());
            String formattedPrice = String.format("%.2f", price);

            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText("🔔 Уведомление: " + setting.getCurrency() +
                    " сейчас стоит $" + formattedPrice);
            message.setParseMode("HTML");

            execute(message);

            logger.info("Уведомление отправлено для {}: {} = ${}",
                    chatId, setting.getCurrency(), formattedPrice);
        }
        catch (Exception e)
        {
            logger.error("Ошибка при отправке уведомления для {}",
                    setting.getCurrency(), e);

            try
            {
                SendMessage message = new SendMessage();
                message.setChatId(String.valueOf(chatId));
                message.setText("❌ Не удалось получить цену для " + setting.getCurrency() +
                        ". Попробуйте позже.");
                execute(message);
            }
            catch (Exception ex)
            {
                logger.error("Ошибка при отправке сообщения об ошибке", ex);
            }
        }
    }

    private void sendCurrencyChart(long chatId, String currency)
    {
        try
        {
            // Получаем исторические данные за 30 дней
            List<CryptoApiService.HistoricalData> history = apiService.getHistoricalData(currency, "day", 30);

            if (history == null || history.isEmpty())
            {
                sendMessage(chatId, "❌ Не удалось получить данные для построения графика");
                return;
            }

            // Формируем данные для графика
            List<String> labels = new ArrayList<>();
            List<Double> prices = new ArrayList<>();

            for (CryptoApiService.HistoricalData data : history)
            {
                // Форматируем дату (день.месяц)
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM");
                String dateStr = sdf.format(new Date(data.getTimestamp() * 1000));
                labels.add(dateStr);
                prices.add(data.getPrice());
            }

            // Генерируем URL для QuickChart
            String chartUrl = generateQuickChartUrl(currency, labels, prices);

            // Отправляем изображение графика
            SendPhoto photo = new SendPhoto();
            photo.setChatId(String.valueOf(chatId));
            photo.setPhoto(new InputFile(chartUrl));
            photo.setCaption("График курса " + currency + " за последние 30 дней");

            try
            {
                execute(photo);
            }
            catch (TelegramApiException e)
            {
                sendMessage(chatId, "❌ Не удалось загрузить график: " + e.getMessage());
            }

        }
        catch (Exception e)
        {
            logger.error("Ошибка при генерации графика для {}", currency, e);
            sendMessage(chatId, "❌ Не удалось загрузить график: " + e.getMessage());
        }
    }

    private String generateQuickChartUrl(String currency, List<String> labels, List<Double> prices)
    {
        // Формируем JSON конфигурации графика
        String config = String.format(
                "{type:'line',data:{labels:%s,datasets:[{label:'%s/USD',data:%s," +
                        "borderColor:'#36A2EB',backgroundColor:'rgba(54,162,235,0.1)',fill:true}]}, " +
                        "options:{responsive:true,maintainAspectRatio:false,scales:{y:{beginAtZero:false}}}}",
                new Gson().toJson(labels),
                currency,
                new Gson().toJson(prices)
        );

        // Кодируем JSON для URL
        return "https://quickchart.io/chart?c=" + URLEncoder.encode(config, StandardCharsets.UTF_8);
    }

    @Override
    public String getBotUsername()
    {
        return "crypto_monitor666_bot";
    }

    @Override
    public String getBotToken()
    {
        // Получаем токен из переменной окружения
        String token = System.getenv("BOT_TOKEN");

        // Проверяем наличие токена
        if (token == null || token.isEmpty())
        {
            throw new IllegalStateException(
                    "Токен бота не установлен!\n" +
                            "Добавьте переменную окружения BOT_TOKEN в систему или IDE:\n" +
                            "Пример для Windows: setx BOT_TOKEN \"ваш_токен\"\n" +
                            "Пример для Linux/macOS: export BOT_TOKEN=\"ваш_токен\""
            );
        }

        return token;
    }
}