package com.example.personalschedule.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.personalschedule.R;
import com.example.personalschedule.activities.EventDetailActivity;
import com.example.personalschedule.models.Event;
import com.example.personalschedule.receivers.EventReminderReceiver;

import java.util.Date;

public class NotificationUtils {

    public static final String CHANNEL_ID = "event_reminder_channel";
    private static final String CHANNEL_NAME = "Event Reminders";
    private static final String CHANNEL_DESCRIPTION = "Notifications for scheduled events";

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(CHANNEL_DESCRIPTION);

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    public static void scheduleEventReminder(Context context, Event event) {
        // Thay vì kiểm tra reminderMinutes, thêm logic xử lý mới
        // Lên lịch nhắc nhở mặc định 15 phút trước khi sự kiện bắt đầu

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, EventReminderReceiver.class);
        intent.putExtra(Constants.EXTRA_EVENT_ID, event.getId());
        intent.putExtra(Constants.EXTRA_EVENT_TITLE, event.getTitle());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) event.getId(), // Cast to int để tương thích với phương thức cũ
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Sử dụng thời gian nhắc nhở mặc định là 15 phút trước sự kiện
        final int DEFAULT_REMINDER_MINUTES = 15;
        long reminderTime = event.getStartTime() - (DEFAULT_REMINDER_MINUTES * 60 * 1000);

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SCHEDULE_EXACT_ALARM) == PackageManager.PERMISSION_GRANTED) {
            Log.d("NotificationUtils", "Quyền SCHEDULE_EXACT_ALARM đã được cấp");
            // Tiến hành lên lịch báo thức
            Log.d("NotificationUtils", "Thời gian nhắc nhở: " + new Date(reminderTime));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminderTime,
                        pendingIntent
                );
            } else {
                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        reminderTime,
                        pendingIntent
                );
            }
        } else {
            Log.w("NotificationUtils", "Quyền SCHEDULE_EXACT_ALARM chưa được cấp!");
            // Xử lý trường hợp không có quyền (ví dụ: thông báo cho người dùng, sử dụng báo thức không chính xác)
            return; // Dừng lại nếu không có quyền
        }
    }

    public static void cancelEventReminder(Context context, long eventId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, EventReminderReceiver.class);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) eventId, // Cast to int vì PendingIntent yêu cầu int
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.cancel(pendingIntent);
        pendingIntent.cancel();
    }
}