package org.example.cryptobot;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main
{
    public static void main(String[] args)
    {
        try
        {
            // Используем DefaultBotSession как параметр
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new CryptoBot());
        }
        catch (TelegramApiException e)
        {
            e.printStackTrace();
        }
    }
}