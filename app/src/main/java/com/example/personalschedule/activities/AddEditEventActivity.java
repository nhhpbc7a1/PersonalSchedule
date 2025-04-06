package com.example.personalschedule.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.personalschedule.R;
import com.example.personalschedule.models.CalendarInfo;
import com.example.personalschedule.models.Event;
import com.example.personalschedule.utils.Constants;
import com.example.personalschedule.utils.DateTimeUtils;
import com.example.personalschedule.viewmodels.AddEditEventViewModel;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class AddEditEventActivity extends AppCompatActivity {

    private static final String TAG = "AddEditEventActivity";
    private static final int CALENDAR_PERMISSION_REQUEST_CODE = 101;

    private AddEditEventViewModel viewModel;

    private EditText etTitle;
    private TextView tvStartDate;
    private TextView tvStartTime;
    private TextView tvEndDate;
    private TextView tvEndTime;
    private EditText etLocation;
    private EditText etDescription;
    private Switch switchAllDay;
    private Spinner spinnerCalendar;
    private Spinner spinnerReminder;
    private Button btnSave;
    private Button btnCancel;

    private final int[] REMINDER_VALUES = {5, 10, 15, 30, 60, 1440, -1};
    private final String[] REMINDER_LABELS = {"5 phút trước", "10 phút trước", "15 phút trước", 
                                              "30 phút trước", "1 giờ trước", "1 ngày trước", "Tùy chỉnh..."};
    private int selectedReminderMinutes = 15;

    private Calendar startCalendar = Calendar.getInstance();
    private Calendar endCalendar = Calendar.getInstance();

    private long eventId;
    private boolean isEditing = false;
    private Event currentEvent = null;

    private final ActivityResultLauncher<String[]> requestCalendarPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean readGranted = result.getOrDefault(Manifest.permission.READ_CALENDAR, false);
                Boolean writeGranted = result.getOrDefault(Manifest.permission.WRITE_CALENDAR, false);
                if (readGranted && writeGranted) {
                    Log.d(TAG, "Calendar permissions granted.");
                    if (isEditing && eventId > 0) {
                        viewModel.loadEventById(eventId);
                    }
                } else {
                    Log.w(TAG, "Calendar permissions denied.");
                    Toast.makeText(this, "Quyền truy cập lịch bị từ chối. Không thể lưu hoặc xem sự kiện lịch.", Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    private final ActivityResultLauncher<Intent> requestExactAlarmLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (checkExactAlarmPermission()) {
                    Toast.makeText(this, "Quyền SCHEDULE_EXACT_ALARM đã được cấp", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Quyền SCHEDULE_EXACT_ALARM bị từ chối", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_event);

        eventId = getIntent().getLongExtra(Constants.EXTRA_EVENT_ID, -1L);
        isEditing = eventId > 0;

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(isEditing ? "Chỉnh sửa sự kiện" : "Thêm sự kiện mới");
        }

        initializeViews();

        setupReminderSpinner();

        setupCalendarSpinner();

        viewModel = new ViewModelProvider(this).get(AddEditEventViewModel.class);

        if (!checkAndRequestCalendarPermissions()) {
            Log.i(TAG, "Calendar permissions not granted yet. Requesting...");
        } else {
            Log.d(TAG, "Calendar permissions already granted.");
            if (isEditing) {
                viewModel.loadEventById(eventId);
            }
        }

        viewModel.getEventData().observe(this, event -> {
            if (event != null) {
                currentEvent = event;
                Log.d(TAG, "Observed event data: " + event.getId());
                populateUI(event);
            } else if (isEditing) {
                Log.e(TAG, "Failed to load event with ID: " + eventId);
                Toast.makeText(this, "Không thể tải thông tin sự kiện", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        viewModel.getSaveResult().observe(this, result -> {
            if (result == null) return;

            if (result.isSuccess()) {
                long savedEventId = result.getEventId();
                Log.d(TAG, "Event saved successfully with ID: " + savedEventId);
                Toast.makeText(this, "Lưu sự kiện thành công", Toast.LENGTH_SHORT).show();

                setResult(isEditing ? Constants.RESULT_EVENT_UPDATED : Constants.RESULT_EVENT_ADDED);
                finish();
            } else {
                Log.e(TAG, "Failed to save event: " + result.getErrorMessage());
                Toast.makeText(this, "Lỗi khi lưu sự kiện: " + result.getErrorMessage(), Toast.LENGTH_LONG).show();
            }
            viewModel.resetSaveResult();
        });

        setupListeners();

        if (!checkExactAlarmPermission()) {
            showExactAlarmPermissionRequestDialog();
        }

        viewModel.getAvailableCalendars().observe(this, calendars -> {
            if (calendars != null) {
                updateCalendarSpinner(calendars);
            }
        });

        viewModel.loadAvailableCalendars();
    }

    private void initializeViews() {
        etTitle = findViewById(R.id.et_title);
        tvStartDate = findViewById(R.id.tv_start_date);
        tvStartTime = findViewById(R.id.tv_start_time);
        tvEndDate = findViewById(R.id.tv_end_date);
        tvEndTime = findViewById(R.id.tv_end_time);
        etLocation = findViewById(R.id.et_location);
        etDescription = findViewById(R.id.et_description);
        switchAllDay = findViewById(R.id.switch_all_day);
        spinnerCalendar = findViewById(R.id.spinner_calendar);
        spinnerReminder = findViewById(R.id.spinner_reminder);
        btnSave = findViewById(R.id.btn_save);
        btnCancel = findViewById(R.id.btn_cancel);
    }

    private void populateUI(@NonNull Event event) {
        etTitle.setText(event.getTitle());

        startCalendar.setTimeInMillis(event.getStartTime());
        if(event.getEndTime() >= event.getStartTime()) {
            endCalendar.setTimeInMillis(event.getEndTime());
        } else {
            endCalendar.setTimeInMillis(event.getStartTime());
            endCalendar.add(Calendar.HOUR_OF_DAY, 1);
        }

        updateDateTimeFields();

        etLocation.setText(event.getLocation());
        etDescription.setText(event.getDescription());

        switchAllDay.setChecked(event.isAllDay());
        updateTimeVisibility(event.isAllDay());

        selectedReminderMinutes = event.getReminderMinutes();
        setReminderSpinnerSelection(selectedReminderMinutes);
    }

    private void setupListeners() {
        tvStartDate.setOnClickListener(v -> showDatePicker(true));
        tvStartTime.setOnClickListener(v -> showTimePicker(true));
        tvEndDate.setOnClickListener(v -> showDatePicker(false));
        tvEndTime.setOnClickListener(v -> showTimePicker(false));

        switchAllDay.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateTimeVisibility(isChecked);
        });
        
        spinnerReminder.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int reminderValue = REMINDER_VALUES[position];
                if (reminderValue == -1) {
                    showCustomReminderDialog();
                } else {
                    selectedReminderMinutes = reminderValue;
                }
            }
            
            @Override 
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        btnSave.setOnClickListener(v -> {
            if (checkAndRequestCalendarPermissions()) {
                saveEvent();
            } else {
                Toast.makeText(this, "Cần cấp quyền truy cập Lịch để lưu sự kiện.", Toast.LENGTH_SHORT).show();
            }
        });

        btnCancel.setOnClickListener(v -> finish());
    }

    private void setupReminderSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, REMINDER_LABELS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerReminder.setAdapter(adapter);
    }

    private void setupCalendarSpinner() {
        ArrayAdapter<String> tempAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, new String[]{"Đang tải danh sách lịch..."});
        tempAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCalendar.setAdapter(tempAdapter);
    }

    private void updateCalendarSpinner(List<CalendarInfo> calendars) {
        if (calendars == null || calendars.isEmpty()) {
            ArrayAdapter<String> noCalendarAdapter = new ArrayAdapter<>(
                    this, android.R.layout.simple_spinner_item, new String[]{"Không tìm thấy lịch nào"});
            noCalendarAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerCalendar.setAdapter(noCalendarAdapter);
            return;
        }

        ArrayAdapter<CalendarInfo> adapter = new ArrayAdapter<CalendarInfo>(
                this, android.R.layout.simple_spinner_item, calendars) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView textView = (TextView) super.getView(position, convertView, parent);
                CalendarInfo calendar = getItem(position);
                if (calendar != null) {
                    textView.setText(calendar.getDisplayName());
                }
                return textView;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView textView = (TextView) super.getDropDownView(position, convertView, parent);
                CalendarInfo calendar = getItem(position);
                if (calendar != null) {
                    textView.setText(calendar.getDisplayName());
                }
                return textView;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCalendar.setAdapter(adapter);

        spinnerCalendar.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                CalendarInfo selectedCalendar = (CalendarInfo) parent.getItemAtPosition(position);
                if (selectedCalendar != null) {
                    viewModel.setSelectedCalendarId(selectedCalendar.getId());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        if (isEditing && currentEvent != null) {
            long eventCalendarId = currentEvent.getCalendarId();
            for (int i = 0; i < calendars.size(); i++) {
                if (calendars.get(i).getId() == eventCalendarId) {
                    spinnerCalendar.setSelection(i);
                    break;
                }
            }
        }
    }

    private void showDatePicker(boolean isStartDate) {
        Calendar calendar = isStartDate ? startCalendar : endCalendar;
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    if (isStartDate) {
                        if (endCalendar.before(startCalendar)) {
                            endCalendar.setTimeInMillis(startCalendar.getTimeInMillis());
                            endCalendar.set(Calendar.HOUR_OF_DAY, startCalendar.get(Calendar.HOUR_OF_DAY));
                            endCalendar.set(Calendar.MINUTE, startCalendar.get(Calendar.MINUTE));
                            endCalendar.add(Calendar.HOUR_OF_DAY, 1);
                        }
                    }
                    updateDateTimeFields();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        if (!isStartDate) {
            datePickerDialog.getDatePicker().setMinDate(startCalendar.getTimeInMillis());
        }
        datePickerDialog.show();
    }

    private void showTimePicker(boolean isStartTime) {
        if (switchAllDay.isChecked()) return;

        Calendar calendar = isStartTime ? startCalendar : endCalendar;
        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    calendar.set(Calendar.MINUTE, minute);
                    calendar.set(Calendar.SECOND, 0);
                    calendar.set(Calendar.MILLISECOND, 0);

                    if (isStartTime) {
                        if (endCalendar.before(startCalendar)) {
                            endCalendar.setTimeInMillis(startCalendar.getTimeInMillis());
                            endCalendar.add(Calendar.HOUR_OF_DAY, 1);
                        }
                    } else {
                        if(DateTimeUtils.isSameDay(startCalendar, endCalendar) && endCalendar.before(startCalendar)){
                            Toast.makeText(this, "Thời gian kết thúc không thể trước thời gian bắt đầu", Toast.LENGTH_SHORT).show();
                            endCalendar.setTimeInMillis(startCalendar.getTimeInMillis());
                            endCalendar.add(Calendar.HOUR_OF_DAY, 1);
                        }
                    }
                    updateDateTimeFields();
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
        );
        timePickerDialog.show();
    }

    private void updateDateTimeFields() {
        tvStartDate.setText(DateTimeUtils.formatDate(startCalendar.getTimeInMillis()));
        tvStartTime.setText(DateTimeUtils.formatTime(startCalendar.getTimeInMillis()));
        tvEndDate.setText(DateTimeUtils.formatDate(endCalendar.getTimeInMillis()));
        tvEndTime.setText(DateTimeUtils.formatTime(endCalendar.getTimeInMillis()));
    }

    private void updateTimeVisibility(boolean isAllDay) {
        if (isAllDay) {
            tvStartTime.setVisibility(View.GONE);
            tvEndTime.setVisibility(View.GONE);
            startCalendar.set(Calendar.HOUR_OF_DAY, 0);
            startCalendar.set(Calendar.MINUTE, 0);
            startCalendar.set(Calendar.SECOND, 0);
            startCalendar.set(Calendar.MILLISECOND, 0);

            endCalendar.setTimeInMillis(startCalendar.getTimeInMillis());
            endCalendar.add(Calendar.DATE, 1);
        } else {
            tvStartTime.setVisibility(View.VISIBLE);
            tvEndTime.setVisibility(View.VISIBLE);
        }
        updateDateTimeFields();
    }

    private void saveEvent() {
        String title = etTitle.getText().toString().trim();
        if (title.isEmpty()) {
            etTitle.setError("Vui lòng nhập tiêu đề sự kiện");
            etTitle.requestFocus();
            return;
        }

        if (endCalendar.before(startCalendar)) {
            Toast.makeText(this, "Thời gian kết thúc phải sau thời gian bắt đầu", Toast.LENGTH_SHORT).show();
            return;
        }

        String location = etLocation.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        boolean isAllDay = switchAllDay.isChecked();

        long selectedCalendarId = 0;
        try {
            if (viewModel.getSelectedCalendarId().getValue() != null && 
                viewModel.getSelectedCalendarId().getValue() > 0) {
                selectedCalendarId = viewModel.getSelectedCalendarId().getValue();
            }
        } catch (Exception e) {
            Log.e(TAG, "Lỗi khi lấy ID lịch đã chọn", e);
        }
        
        final Event tempEvent = new Event();
        tempEvent.setTitle(title);
        tempEvent.setStartTime(startCalendar.getTimeInMillis());
        tempEvent.setEndTime(endCalendar.getTimeInMillis());
        tempEvent.setLocation(location);
        tempEvent.setDescription(description);
        tempEvent.setAllDay(isAllDay);
        tempEvent.setCalendarId(selectedCalendarId);
        tempEvent.setTimeZone(TimeZone.getDefault().getID());
        tempEvent.setReminderMinutes(selectedReminderMinutes);
        
        final boolean needsLocalCalendar = selectedCalendarId <= 0 && !hasGoogleAccount();
        if (needsLocalCalendar) {
            showNoGoogleAccountWarning(
                () -> saveEventFromTempObject(tempEvent),
                () -> {}
            );
            return;
        }
        
        saveEventFromTempObject(tempEvent);
    }

    private void saveEventFromTempObject(Event eventToSave) {
        Log.d(TAG, "Attempting to save event: " + eventToSave);
        
        if (isEditing && currentEvent != null) {
            eventToSave.setId(currentEvent.getId());
        }
        
        viewModel.saveEvent(eventToSave);
    }

    private boolean hasGoogleAccount() {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    CalendarContract.Calendars.CONTENT_URI,
                    new String[]{CalendarContract.Calendars._ID},
                    CalendarContract.Calendars.ACCOUNT_TYPE + " = ?",
                    new String[]{"com.google"},
                    null);
            return cursor != null && cursor.getCount() > 0;
        } catch (Exception e) {
            Log.e(TAG, "Error checking Google account", e);
            return false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void showNoGoogleAccountWarning(Runnable onProceed, Runnable onCancel) {
        new AlertDialog.Builder(this)
            .setTitle("Thông báo")
            .setMessage("Thiết bị của bạn chưa đăng nhập vào tài khoản Google Calendar. " +
                       "Sự kiện sẽ được lưu vào lịch cục bộ và không đồng bộ với Google Calendar. " +
                       "Bạn có muốn tiếp tục?")
            .setPositiveButton("Tiếp tục", (dialog, which) -> {
                onProceed.run();
            })
            .setNegativeButton("Hủy", (dialog, which) -> {
                onCancel.run();
            })
            .setCancelable(false)
            .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean checkCalendarPermissions() {
        boolean readGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
        boolean writeGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
        return readGranted && writeGranted;
    }

    private boolean checkAndRequestCalendarPermissions() {
        if (!checkCalendarPermissions()) {
            requestCalendarPermissionLauncher.launch(new String[]{
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
            });
            return false;
        }
        return true;
    }

    private boolean checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            return alarmManager.canScheduleExactAlarms();
        } else {
            return true;
        }
    }

    private void showExactAlarmPermissionRequestDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            new AlertDialog.Builder(this)
                    .setTitle("Yêu cầu quyền (Tùy chọn)")
                    .setMessage("Để đặt lịch nhắc nhở chính xác (nếu sử dụng), ứng dụng cần quyền đặc biệt. Bạn có thể cấp quyền này trong Cài đặt hệ thống.")
                    .setPositiveButton("Đi tới Cài đặt", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                        try {
                            requestExactAlarmLauncher.launch(intent);
                        } catch (Exception e) {
                            Log.e(TAG, "Cannot open ACTION_REQUEST_SCHEDULE_EXACT_ALARM settings", e);
                            Toast.makeText(this, "Không thể mở cài đặt quyền báo thức.", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Bỏ qua", null)
                    .show();
        }
    }

    private void showCustomReminderDialog() {
        final String[] timeUnits = {"phút", "giờ", "ngày"};
        final int[] selectedUnit = {0};
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("Nhập số");
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Nhắc nhở trước");
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(50, 20, 50, 20);
        
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        input.setLayoutParams(inputParams);
        
        Spinner unitSpinner = new Spinner(this);
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, timeUnits);
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        unitSpinner.setAdapter(unitAdapter);
        unitSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedUnit[0] = position;
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        spinnerParams.setMarginStart(20);
        unitSpinner.setLayoutParams(spinnerParams);
        
        layout.addView(input);
        layout.addView(unitSpinner);
        builder.setView(layout);
        
        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                int value = Integer.parseInt(input.getText().toString());
                if (value <= 0) {
                    Toast.makeText(this, "Vui lòng nhập số dương", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                switch (selectedUnit[0]) {
                    case 0:
                        selectedReminderMinutes = value;
                        break;
                    case 1:
                        selectedReminderMinutes = value * 60;
                        break;
                    case 2:
                        selectedReminderMinutes = value * 24 * 60;
                        break;
                }
                
                updateReminderSpinnerWithCustomValue();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Vui lòng nhập một số hợp lệ", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("Hủy", (dialog, which) -> {
            spinnerReminder.setSelection(2);
            selectedReminderMinutes = 15;
        });
        
        builder.show();
    }
    
    private void updateReminderSpinnerWithCustomValue() {
        String customLabel = formatReminderTimeToString(selectedReminderMinutes);
        
        String[] customLabels = REMINDER_LABELS.clone();
        customLabels[customLabels.length - 1] = customLabel + " (Tùy chỉnh)";
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, customLabels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerReminder.setAdapter(adapter);
        spinnerReminder.setSelection(customLabels.length - 1);
    }
    
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

    private void setReminderSpinnerSelection(int minutes) {
        for (int i = 0; i < REMINDER_VALUES.length; i++) {
            if (REMINDER_VALUES[i] == minutes) {
                spinnerReminder.setSelection(i);
                return;
            }
        }
        
        updateReminderSpinnerWithCustomValue();
    }
}