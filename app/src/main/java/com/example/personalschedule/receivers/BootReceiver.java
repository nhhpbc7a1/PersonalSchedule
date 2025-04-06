package com.example.personalschedule.receivers;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.personalschedule.database.EventRepository;
import com.example.personalschedule.models.Event;
import com.example.personalschedule.utils.NotificationUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Re-schedule all event reminders
            rescheduleEventReminders(context);
        }
    }

    private void rescheduleEventReminders(Context context) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            EventRepository repository = new EventRepository((Application) context.getApplicationContext());
            List<Event> allEvents = repository.getAllEvents().getValue(); // Get the current value synchronously

            if (allEvents != null) {
                for (Event event : allEvents) {
                    // Không còn sử dụng getReminderMinutes() vì đã bị loại bỏ
                    // Lên lịch thông báo cho tất cả sự kiện sắp tới
                    // Thay vì kiểm tra reminderMinutes, có thể dựa vào thời gian bắt đầu của sự kiện
                    long currentTime = System.currentTimeMillis();
                    if (event.getStartTime() > currentTime) {
                        NotificationUtils.scheduleEventReminder(context, event);
                    }
                }
            }
        });
    }
}