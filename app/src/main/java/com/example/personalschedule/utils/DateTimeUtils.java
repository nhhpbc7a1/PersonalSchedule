package com.example.personalschedule.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone; // Thêm import TimeZone

public class DateTimeUtils {

    // --- Formatters (Giữ nguyên) ---
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private static final SimpleDateFormat DAY_OF_WEEK_FORMAT = new SimpleDateFormat("EEEE", Locale.getDefault()); // Ví dụ: Thứ Hai
    private static final SimpleDateFormat MONTH_YEAR_FORMAT = new SimpleDateFormat("MMMM yyyy", Locale.getDefault()); // Ví dụ: Tháng Ba 2024

    // --- Formatting Methods ---

    // Giữ lại phiên bản cũ nhận Date nếu vẫn còn dùng ở đâu đó
    public static String formatTime(Date date) {
        if (date == null) return "";
        return TIME_FORMAT.format(date);
    }

    // Thêm phiên bản mới nhận long timestamp
    public static String formatTime(long timeInMillis) {
        if (timeInMillis <= 0) return "";
        return TIME_FORMAT.format(new Date(timeInMillis));
    }

    // Giữ lại phiên bản cũ nhận Date
    public static String formatDate(Date date) {
        if (date == null) return "";
        return DATE_FORMAT.format(date);
    }

    // Thêm phiên bản mới nhận long timestamp
    public static String formatDate(long timeInMillis) {
        if (timeInMillis <= 0) return "";
        return DATE_FORMAT.format(new Date(timeInMillis));
    }

    // Giữ lại phiên bản cũ nhận Date
    public static String formatDayOfWeek(Date date) {
        if (date == null) return "";
        return DAY_OF_WEEK_FORMAT.format(date);
    }

    // Thêm phiên bản mới nhận long timestamp
    public static String formatDayOfWeek(long timeInMillis) {
        if (timeInMillis <= 0) return "";
        return DAY_OF_WEEK_FORMAT.format(new Date(timeInMillis));
    }

    // Giữ lại phiên bản cũ nhận Date
    public static String formatMonthYear(Date date) {
        if (date == null) return "";
        return MONTH_YEAR_FORMAT.format(date);
    }

    // Thêm phiên bản mới nhận long timestamp
    public static String formatMonthYear(long timeInMillis) {
        if (timeInMillis <= 0) return "";
        return MONTH_YEAR_FORMAT.format(new Date(timeInMillis));
    }

    // --- Key Generation ---

    // Giữ lại phiên bản cũ nhận Date
    public static String getMonthYearKey(Date date) {
        if (date == null) return "";
        SimpleDateFormat format = new SimpleDateFormat("MM-yyyy", Locale.getDefault());
        return format.format(date);
    }

    // Thêm phiên bản mới nhận long timestamp
    public static String getMonthYearKey(long timeInMillis) {
        if (timeInMillis <= 0) return "";
        SimpleDateFormat format = new SimpleDateFormat("MM-yyyy", Locale.getDefault());
        return format.format(new Date(timeInMillis));
    }

    // --- Date/Time Manipulation & Comparison ---

    // Giữ lại phiên bản cũ nhận Date (có thể không cần nữa)
    public static Date getDateWithoutTime(Date date) {
        if (date == null) return null;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    // Thêm phiên bản mới nhận long và trả về long
    public static long getStartOfDayInMillis(long timeInMillis) {
        if (timeInMillis <= 0) return 0;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeInMillis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    // Xóa hoặc đánh dấu @Deprecated vì Event không còn reminderMinutes
    // public static long getEventReminderTimeInMillis(Date eventDate, int reminderMinutes) { ... }
    // public static long getEventReminderTimeInMillis(long eventTimeMillis, int reminderMinutes) { ... }


    // Giữ lại phiên bản cũ nhận Date
    public static boolean isToday(Date date) {
        if (date == null) return false;
        return isSameDay(Calendar.getInstance(), dateToCalendar(date));
    }

    // Thêm phiên bản mới nhận long timestamp
    public static boolean isToday(long timeInMillis) {
        if (timeInMillis <= 0) return false;
        return isSameDay(Calendar.getInstance(), millisToCalendar(timeInMillis));
    }

    // Giữ lại phiên bản cũ nhận Date
    public static boolean isTomorrow(Date date) {
        if (date == null) return false;
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        return isSameDay(tomorrow, dateToCalendar(date));
    }

    // Thêm phiên bản mới nhận long timestamp
    public static boolean isTomorrow(long timeInMillis) {
        if (timeInMillis <= 0) return false;
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        return isSameDay(tomorrow, millisToCalendar(timeInMillis));
    }

    /**
     * THÊM PHƯƠNG THỨC NÀY: Kiểm tra xem hai đối tượng Calendar có trỏ đến cùng một ngày hay không
     * (bỏ qua thành phần thời gian).
     * @param cal1 Calendar thứ nhất.
     * @param cal2 Calendar thứ hai.
     * @return true nếu cùng ngày, false nếu khác ngày hoặc có cal là null.
     */
    public static boolean isSameDay(Calendar cal1, Calendar cal2) {
        if (cal1 == null || cal2 == null) {
            return false;
        }
        return cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) &&
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    // --- Helper Methods ---
    private static Calendar dateToCalendar(Date date) {
        if (date == null) return null;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar;
    }

    private static Calendar millisToCalendar(long timeInMillis) {
        if (timeInMillis <= 0) return null;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeInMillis);
        return calendar;
    }

    // Optional: Helper to get Calendar instance with specific timezone if needed elsewhere
    public static Calendar getCalendarInstance(String timeZoneId) {
        try {
            TimeZone tz = TimeZone.getTimeZone(timeZoneId);
            return Calendar.getInstance(tz);
        } catch (Exception e) {
            // Return default timezone if invalid ID is provided
            return Calendar.getInstance();
        }
    }
}