package com.example.personalschedule.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils; // Thêm import TextUtils
import android.util.Log; // Thêm Log
import android.view.Menu;
import android.view.MenuItem;
import android.view.View; // Thêm View
// import android.widget.ImageView; // Xóa import không dùng
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
// import androidx.core.content.ContextCompat; // Xóa import không dùng
import androidx.lifecycle.ViewModelProvider;

import com.example.personalschedule.R;
import com.example.personalschedule.models.Event;
import com.example.personalschedule.utils.Constants;
import com.example.personalschedule.utils.DateTimeUtils;
// import com.example.personalschedule.utils.NotificationUtils; // Xóa import không dùng

import com.example.personalschedule.viewmodels.EventDetailViewModel; // Đảm bảo ViewModel đúng

import java.util.Locale; // Thêm import Locale

public class EventDetailActivity extends AppCompatActivity {

    private static final String TAG = "EventDetailActivity";

    private EventDetailViewModel viewModel;

    private TextView tvTitle;
    private TextView tvDate;
    private TextView tvTime;
    private TextView tvLocation;
    private TextView tvDescription;
    private TextView tvReminder;
    // private TextView tvRecurrence;
    // private ImageView ivPriority; // XÓA

    // private int eventId; // Sửa thành long
    private long eventId;
    private Event currentEvent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_detail);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Chi tiết sự kiện");
        }

        initializeViews();

        // Get event ID from intent (sử dụng long)
        eventId = getIntent().getLongExtra(Constants.EXTRA_EVENT_ID, -1L); // Sử dụng getLongExtra và -1L
        if (eventId <= 0) { // ID hợp lệ của Calendar Provider luôn > 0
            Log.e(TAG, "Invalid event ID received: " + eventId);
            Toast.makeText(this, "Lỗi: ID sự kiện không hợp lệ", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Log.d(TAG, "Displaying details for event ID: " + eventId);

        // Set up ViewModel
        // TODO: Đảm bảo EventDetailViewModel được cập nhật để xử lý long ID
        viewModel = new ViewModelProvider(this).get(EventDetailViewModel.class);
        viewModel.loadEvent(eventId); // Giả sử ViewModel có phương thức loadEvent(long id)

        // Observe event data
        viewModel.getEvent().observe(this, event -> {
            if (event != null) {
                currentEvent = event;
                displayEventDetails(event);
            } else {
                // Event có thể đã bị xóa hoặc không load được
                Log.w(TAG, "ViewModel returned null event for ID: " + eventId);
                Toast.makeText(this, "Không thể tải thông tin sự kiện.", Toast.LENGTH_SHORT).show();
                // Không nên finish() ngay lập tức, có thể do lỗi tạm thời hoặc quyền
            }
        });

        // Observe delete operation result
        // TODO: Đảm bảo EventDetailViewModel trả về kết quả xóa (ví dụ: LiveData<Boolean>)
        viewModel.getDeleteResult().observe(this, success -> { // Giả sử có getDeleteResult
            if (success != null && success) {
                Log.d(TAG, "Event deleted successfully (ID: " + eventId + ")");
                Toast.makeText(this, "Đã xóa sự kiện", Toast.LENGTH_SHORT).show();

                // ---- XÓA LOGIC CANCEL NOTIFICATION ----
                // NotificationUtils.cancelEventReminder(this, eventId); // Không còn phù hợp
                // ---- ----

                setResult(Constants.RESULT_EVENT_DELETED); // Gửi kết quả về màn hình trước
                finish();
            } else if (success != null /* && !success */) {
                // Chỉ hiển thị lỗi nếu success là false, không phải null
                Log.e(TAG, "Failed to delete event (ID: " + eventId + ")");
                Toast.makeText(this, "Lỗi khi xóa sự kiện", Toast.LENGTH_SHORT).show();
            }
            // Có thể cần reset trạng thái trong ViewModel sau khi xử lý
        });
    }

    private void initializeViews() {
        tvTitle = findViewById(R.id.tv_event_title);
        tvDate = findViewById(R.id.tv_event_date);
        tvTime = findViewById(R.id.tv_event_time);
        tvLocation = findViewById(R.id.tv_event_location);
        tvDescription = findViewById(R.id.tv_event_description);
        tvReminder = findViewById(R.id.tv_event_reminder);
    }

    private void displayEventDetails(Event event) {
        tvTitle.setText(event.getTitle());

        // Sử dụng phương thức format nhận long timestamp
        tvDate.setText(DateTimeUtils.formatDate(event.getStartTime()));

        if (event.isAllDay()) {
            tvTime.setText(R.string.all_day);
        } else {
            // Đảm bảo endTime hợp lệ trước khi format
            String endTimeFormatted = (event.getEndTime() >= event.getStartTime())
                    ? DateTimeUtils.formatTime(event.getEndTime())
                    : "?"; // Hoặc không hiển thị nếu không hợp lệ
            tvTime.setText(String.format(Locale.getDefault(), "%s - %s",
                    DateTimeUtils.formatTime(event.getStartTime()),
                    endTimeFormatted));
        }

        // Kiểm tra null và empty cho location
        if (!TextUtils.isEmpty(event.getLocation())) {
            tvLocation.setText(event.getLocation());
            tvLocation.setVisibility(View.VISIBLE); // Hiển thị nếu có
        } else {
            // tvLocation.setText(R.string.no_location); // Có thể không cần set text
            tvLocation.setVisibility(View.GONE); // Ẩn nếu không có
        }

        // Kiểm tra null và empty cho description
        if (!TextUtils.isEmpty(event.getDescription())) {
            tvDescription.setText(event.getDescription());
            tvDescription.setVisibility(View.VISIBLE); // Hiển thị nếu có
        } else {
            // tvDescription.setText(R.string.no_description); // Có thể không cần set text
            tvDescription.setVisibility(View.GONE); // Ẩn nếu không có
        }
        
        // Hiển thị thông tin lời nhắc
        if (tvReminder != null) {
            int reminderMinutes = event.getReminderMinutes();
            if (reminderMinutes > 0) {
                String reminderText = formatReminderTimeToString(reminderMinutes);
                tvReminder.setText(reminderText);
                tvReminder.setVisibility(View.VISIBLE);
                
                View reminderContainer = findViewById(R.id.ll_reminder);
                if (reminderContainer != null) {
                    reminderContainer.setVisibility(View.VISIBLE);
                }
            } else {
                tvReminder.setText(R.string.no_reminder);
                
                View reminderContainer = findViewById(R.id.ll_reminder);
                if (reminderContainer != null) {
                    reminderContainer.setVisibility(View.VISIBLE);
                }
            }
        }
    }
    
    // Định dạng thời gian lời nhắc dưới dạng văn bản
    private String formatReminderTimeToString(int minutes) {
        if (minutes < 60) {
            return minutes + " phút trước";
        } else if (minutes < 1440) {
            int hours = minutes / 60;
            return hours + " giờ trước";
        } else {
            int days = minutes / 1440;
            return days + " ngày trước";
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_event_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == android.R.id.home) {
            finish(); // Sử dụng finish() thay vì onBackPressed() để tôn trọng luồng Activity
            return true;
        } else if (itemId == R.id.action_edit) {
            editEvent();
            return true;
        } else if (itemId == R.id.action_delete) {
            confirmDelete();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void editEvent() {
        if (currentEvent == null || currentEvent.getId() <= 0) {
            Toast.makeText(this, "Không thể sửa sự kiện này.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, AddEditEventActivity.class);
        // Truyền eventId dạng long
        intent.putExtra(Constants.EXTRA_EVENT_ID, currentEvent.getId()); // <<<< SỬA Ở ĐÂY
        // Không cần startActivityForResult nếu bạn chỉ cần finish() khi quay lại
        // Nếu cần cập nhật lại màn hình này sau khi edit, dùng ActivityResultLauncher
        startActivity(intent);
        // Có thể finish() màn hình detail sau khi mở màn hình edit
        // finish();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Xóa sự kiện")
                .setMessage("Bạn có chắc chắn muốn xóa sự kiện \"" + (currentEvent != null ? currentEvent.getTitle() : "") + "\"?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    if (currentEvent != null) {
                        Log.d(TAG,"Requesting delete for event ID: " + currentEvent.getId());
                        // TODO: Đảm bảo ViewModel có phương thức deleteEvent(Event) hoặc deleteEventById(long)
                        viewModel.deleteEvent(currentEvent); // Giả sử ViewModel có phương thức này
                    } else {
                        Log.w(TAG,"Delete confirmation but currentEvent is null.");
                        Toast.makeText(this, "Không thể xóa, dữ liệu sự kiện không tồn tại.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // onActivityResult đã bị deprecated, nên sử dụng ActivityResultLauncher
    // Nếu bạn cần kết quả trả về từ AddEditEventActivity để cập nhật màn hình này,
    // bạn cần triển khai ActivityResultLauncher.
    // Hiện tại, logic chỉ là setResult() nếu xóa thành công, không xử lý kết quả edit.

    /* // Ví dụ về cách dùng ActivityResultLauncher thay cho onActivityResult
    private final ActivityResultLauncher<Intent> editEventLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == Constants.RESULT_EVENT_UPDATED) {
                // Event đã được cập nhật, yêu cầu ViewModel load lại dữ liệu
                 Log.d(TAG,"Event updated, reloading details for ID: " + eventId);
                viewModel.loadEvent(eventId); // Load lại để hiển thị thay đổi
                // Thông báo cho màn hình danh sách (MainActivity) nếu cần
                setResult(Constants.RESULT_EVENT_UPDATED);
            }
             // Xử lý các result code khác nếu cần
        });

     private void editEventWithLauncher() {
         if (currentEvent == null || currentEvent.getId() <= 0) { ... }
         Intent intent = new Intent(this, AddEditEventActivity.class);
         intent.putExtra(Constants.EXTRA_EVENT_ID, currentEvent.getId());
         editEventLauncher.launch(intent); // Khởi chạy bằng launcher
     }
     */

}