package com.example.personalschedule.receivers;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.personalschedule.R;
import com.example.personalschedule.utils.Constants;
import com.example.personalschedule.utils.NotificationUtils;

public class EventReminderReceiver extends BroadcastReceiver {
    // KHÔNG cần khởi tạo ở đây
    // private static final Notification CHANNEL_ID = ...;

    @Override
    public void onReceive(Context context, Intent intent) {
        String eventTitle = intent.getStringExtra(Constants.EXTRA_EVENT_TITLE);
        int eventId = intent.getIntExtra(Constants.EXTRA_EVENT_ID, -1);

        Log.i("EventReminderReceiver", "onReceive() called!");
        // Kiểm tra quyền trước
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            Notification notification = new NotificationCompat.Builder(context, NotificationUtils.CHANNEL_ID) // Sử dụng CHANNEL_ID từ NotificationUtils
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Event Reminder")
                    .setContentText(eventTitle)
                    .setPriority(NotificationCompat.PRIORITY_HIGH) // Thêm dòng này
                    .setAutoCancel(true) // Thêm dòng này
                    .build();

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            notificationManager.notify(eventId, notification); // Sử dụng eventId làm ID thông báo
        } else {
            // Xử lý trường hợp không có quyền
            Log.w("EventReminderReceiver", "Không có quyền POST_NOTIFICATIONS");
            // Bạn có thể hiển thị một thông báo cho người dùng rằng họ cần cấp quyền
        }
    }
}