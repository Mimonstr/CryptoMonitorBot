//package org.example.cryptobot;
//
//import java.sql.*;
//import java.util.ArrayList;
//import java.util.List;
//
//public class DatabaseHelper
//{
//    protected static final String DB_URL = "jdbc:sqlite:crypto_bot.db";
//
//
//    public void initDatabase()
//    {
//        try (Connection conn = DriverManager.getConnection(DB_URL);
//             Statement stmt = conn.createStatement())
//        {
//            // Создаем таблицу для избранных валют
//            String sql = "CREATE TABLE IF NOT EXISTS user_currencies (" +
//                    "user_id INTEGER NOT NULL, " +
//                    "currency TEXT NOT NULL, " +
//                    "PRIMARY KEY(user_id, currency))";
//            stmt.execute(sql);
//            System.out.println("База данных создана/готова к работе.");
//        }
//        catch (SQLException e)
//        {
//            e.printStackTrace();
//        }
//    }
//
//    public boolean isCurrencyFavorite(long userId, String currency)
//    {
//        try (Connection conn = DriverManager.getConnection(DB_URL);
//             PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM user_currencies WHERE user_id = ? AND currency = ?"))
//        {
//            stmt.setLong(1, userId);
//            stmt.setString(2, currency.toUpperCase());
//            ResultSet rs = stmt.executeQuery();
//            return rs.next();
//        }
//        catch (SQLException e)
//        {
//            e.printStackTrace();
//            return false;
//        }
//    }
//
//    public void addCurrency(long userId, String currency)
//    {
//        try (Connection conn = DriverManager.getConnection(DB_URL);
//             PreparedStatement pstmt = conn.prepareStatement("INSERT OR REPLACE INTO user_currencies(user_id, currency) VALUES(?, ?)"))
//        {
//            pstmt.setLong(1, userId);
//            pstmt.setString(2, currency.toUpperCase());
//            pstmt.executeUpdate();
//        }
//        catch (SQLException e)
//        {
//            e.printStackTrace();
//        }
//    }
//
//    public void removeCurrency(long userId, String currency)
//    {
//        try (Connection conn = DriverManager.getConnection(DB_URL);
//             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM user_currencies WHERE user_id = ? AND currency = ?"))
//        {
//            pstmt.setLong(1, userId);
//            pstmt.setString(2, currency.toUpperCase());
//            pstmt.executeUpdate();
//        }
//        catch (SQLException e)
//        {
//            e.printStackTrace();
//        }
//    }
//
//    public String getCurrencyList(long userId)
//    {
//        StringBuilder list = new StringBuilder();
//        try (Connection conn = DriverManager.getConnection(DB_URL);
//             PreparedStatement pstmt = conn.prepareStatement("SELECT currency FROM user_currencies WHERE user_id = ?"))
//        {
//            pstmt.setLong(1, userId);
//            ResultSet rs = pstmt.executeQuery();
//            while (rs.next())
//            {
//                list.append(rs.getString("currency")).append("\n");
//            }
//        }
//        catch (SQLException e)
//        {
//            e.printStackTrace();
//        }
//        return list.toString().isEmpty() ? "Список пуст" : list.toString();
//    }
//
//    public List<String> getFavoriteCurrencies(long userId)
//    {
//        List<String> currencies = new ArrayList<>();
//        try (Connection conn = DriverManager.getConnection(DB_URL);
//             PreparedStatement stmt = conn.prepareStatement("SELECT currency FROM user_currencies WHERE user_id = ?"))
//        {
//            stmt.setLong(1, userId);
//            ResultSet rs = stmt.executeQuery();
//            while (rs.next())
//            {
//                currencies.add(rs.getString("currency"));
//            }
//        }
//        catch (SQLException e)
//        {
//            e.printStackTrace();
//        }
//        return currencies;
//    }
//
//}


package org.example.cryptobot;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.example.cryptobot.CryptoBot.logger;

public class DatabaseHelper
{
    protected static final String DB_URL = "jdbc:sqlite:crypto_bot.db";

    public void initDatabase()
    {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement())
        {
            // Создаем таблицу для избранных валют
            String sql = "CREATE TABLE IF NOT EXISTS user_currencies (" +
                    "user_id INTEGER NOT NULL, " +
                    "currency TEXT NOT NULL, " +
                    "PRIMARY KEY(user_id, currency))";
            stmt.execute(sql);

            // Добавляем таблицу для уведомлений
            String notificationSql = "CREATE TABLE IF NOT EXISTS notifications (" +
                    "user_id INTEGER, " +
                    "currency TEXT, " +
                    "interval_minutes INTEGER, " +
                    "last_notification INTEGER, " +
                    "PRIMARY KEY (user_id, currency))";
            stmt.execute(notificationSql);

            System.out.println("База данных создана/готова к работе.");
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }
    }

    public Connection getConnection() throws SQLException
    {
        return DriverManager.getConnection(DB_URL);
    }

    public boolean isCurrencyFavorite(long userId, String currency)
    {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM user_currencies WHERE user_id = ? AND currency = ?"))
        {
            stmt.setLong(1, userId);
            stmt.setString(2, currency.toUpperCase());
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        }
        catch (SQLException e)
        {
            e.printStackTrace();
            return false;
        }
    }

    public void addCurrency(long userId, String currency)
    {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement("INSERT OR REPLACE INTO user_currencies(user_id, currency) VALUES(?, ?)"))
        {
            pstmt.setLong(1, userId);
            pstmt.setString(2, currency.toUpperCase());
            pstmt.executeUpdate();
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }
    }

    public void removeCurrency(long userId, String currency)
    {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM user_currencies WHERE user_id = ? AND currency = ?"))
        {
            pstmt.setLong(1, userId);
            pstmt.setString(2, currency.toUpperCase());
            pstmt.executeUpdate();
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }
    }

    public String getCurrencyList(long userId)
    {
        StringBuilder list = new StringBuilder();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement("SELECT currency FROM user_currencies WHERE user_id = ?"))
        {
            pstmt.setLong(1, userId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next())
            {
                list.append(rs.getString("currency")).append("\n");
            }
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }
        return list.toString().isEmpty() ? "Список пуст" : list.toString();
    }


    public List<String> getFavoriteCurrencies(long userId)
    {
        List<String> currencies = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement("SELECT currency FROM user_currencies WHERE user_id = ?"))
        {
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next())
            {
                currencies.add(rs.getString("currency"));
            }
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }
        return currencies;
    }

    // --- Новые методы для работы с уведомлениями ---
    public void addNotificationSetting(long userId, String currency, int minutes)
    {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT OR REPLACE INTO notifications (user_id, currency, interval_minutes, last_notification) " +
                             "VALUES (?, ?, ?, strftime('%s', 'now'))"))
        {

            stmt.setLong(1, userId);
            stmt.setString(2, currency);
            stmt.setInt(3, minutes);
            stmt.executeUpdate();

            logger.info("Добавлено уведомление: user={}, currency={}, interval={} мин",
                    userId, currency, minutes);
        }
        catch (SQLException e)
        {
            logger.error("Ошибка при добавлении уведомления", e);
        }
    }

    public List<NotificationSetting> getNotificationSettings(long userId)
    {
        List<NotificationSetting> settings = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT currency, interval_minutes FROM notifications WHERE user_id = ?"))
        {
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next())
            {
                settings.add(new NotificationSetting(
                        rs.getString("currency"),
                        rs.getInt("interval_minutes")
                ));
            }
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }
        return settings;
    }

    public boolean isNotificationExists(long userId, String currency)
    {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT 1 FROM notifications WHERE user_id = ? AND currency = ?"))
        {
            stmt.setLong(1, userId);
            stmt.setString(2, currency);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        }
        catch (SQLException e)
        {
            e.printStackTrace();
            return false;
        }
    }

    public static class NotificationSetting
    {
        private final String currency;
        private final int intervalMinutes;

        public NotificationSetting(String currency, int intervalMinutes)
        {
            this.currency = currency;
            this.intervalMinutes = intervalMinutes;
        }

        public String getCurrency()
        {
            return currency;
        }

        public int getIntervalMinutes()
        {
            return intervalMinutes;
        }
    }

    public void removeNotificationSetting(long userId, String currency)
    {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM notifications WHERE user_id = ? AND currency = ?"))
        {
            stmt.setLong(1, userId);
            stmt.setString(2, currency);
            stmt.executeUpdate();
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }
    }

    // --- Метод для обновления времени последнего уведомления ---
    public void updateLastNotificationTime(long userId, String currency)
    {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE notifications SET last_notification = strftime('%s', 'now') " +
                             "WHERE user_id = ? AND currency = ?"))
        {

            stmt.setLong(1, userId);
            stmt.setString(2, currency);
            stmt.executeUpdate();

            logger.debug("Обновлено время последнего уведомления для {}: {}", userId, currency);
        }
        catch (SQLException e)
        {
            logger.error("Ошибка при обновлении времени уведомления", e);
        }
    }

    // --- Метод для получения уведомлений, требующих отправки ---
    public List<NotificationSetting> getNotificationsToProcess(long currentTimestamp)
    {
        List<NotificationSetting> notifications = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT user_id, currency, interval_minutes " +
                             "FROM notifications " +
                             "WHERE strftime('%s', 'now') - last_notification >= interval_minutes * 60"))
        {
            ResultSet rs = stmt.executeQuery();
            while (rs.next())
            {
                notifications.add(new NotificationSetting(
                        rs.getString("currency"),
                        rs.getInt("interval_minutes")
                ));
                // Здесь нужно добавить информацию о user_id, но это требует изменения класса NotificationSetting
                // Либо использовать другой подход для возврата user_id
            }
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }
        return notifications;
    }
}
