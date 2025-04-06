package com.example.personalschedule.database;

import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.text.TextUtils; // Thêm import TextUtils
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.personalschedule.models.CalendarInfo; // Đảm bảo import lớp này
import com.example.personalschedule.models.Event;

import java.util.ArrayList;
import java.util.Calendar;
// import java.util.Date; // Không còn dùng Date trực tiếp ở đây
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Date;
import java.lang.StringBuilder;

public class EventRepository {

    private static final String TAG = "EventRepository"; // Tag for logging

    private ContentResolver contentResolver;
    private ExecutorService executorService;
    private Application application;

    // --- Projection for Events (các cột cần lấy từ Calendar Provider) ---
    private static final String[] EVENT_PROJECTION = new String[]{
            CalendarContract.Events._ID,                 // 0: long
            CalendarContract.Events.CALENDAR_ID,         // 1: long
            CalendarContract.Events.TITLE,               // 2: String
            CalendarContract.Events.EVENT_LOCATION,      // 3: String
            CalendarContract.Events.DESCRIPTION,         // 4: String
            CalendarContract.Events.DTSTART,             // 5: long (milliseconds)
            CalendarContract.Events.DTEND,               // 6: long (milliseconds) - Có thể null nếu dùng DURATION
            CalendarContract.Events.DURATION,            // 7: String (e.g., "P1H" for 1 hour) - Dùng nếu DTEND null
            CalendarContract.Events.EVENT_TIMEZONE,      // 8: String
            CalendarContract.Events.ALL_DAY              // 9: int (0 or 1)
            // Thêm các cột khác nếu cần
    };
    // Indices for EVENT_PROJECTION
    private static final int PROJECTION_ID_INDEX = 0;
    private static final int PROJECTION_CALENDAR_ID_INDEX = 1;
    private static final int PROJECTION_TITLE_INDEX = 2;
    private static final int PROJECTION_LOCATION_INDEX = 3;
    private static final int PROJECTION_DESCRIPTION_INDEX = 4;
    private static final int PROJECTION_DTSTART_INDEX = 5;
    private static final int PROJECTION_DTEND_INDEX = 6;
    private static final int PROJECTION_DURATION_INDEX = 7;
    private static final int PROJECTION_TIMEZONE_INDEX = 8;
    private static final int PROJECTION_ALL_DAY_INDEX = 9;

    // --- Projection for Calendars ---
    private static final String[] CALENDAR_PROJECTION = new String[]{
            CalendarContract.Calendars._ID,                 // 0: long
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, // 1: String
            CalendarContract.Calendars.ACCOUNT_NAME,        // 2: String
            CalendarContract.Calendars.OWNER_ACCOUNT,       // 3: String
            CalendarContract.Calendars.IS_PRIMARY           // 4: int (>=1 if primary, deprecated)
            // Có thể thêm CALENDAR_ACCESS_LEVEL để kiểm tra quyền ghi
            // CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL // 5: int
    };
    // Indices for CALENDAR_PROJECTION
    private static final int PROJECTION_CAL_ID_INDEX = 0;
    private static final int PROJECTION_CAL_DISPLAY_NAME_INDEX = 1;
    private static final int PROJECTION_CAL_ACCOUNT_NAME_INDEX = 2;
    private static final int PROJECTION_CAL_OWNER_ACCOUNT_INDEX = 3;
    private static final int PROJECTION_CAL_IS_PRIMARY_INDEX = 4; // Cẩn thận khi dùng IS_PRIMARY


    public EventRepository(Application application) {
        this.application = application;
        contentResolver = application.getContentResolver();
        // Sử dụng cached thread pool có thể tốt hơn cho nhiều tác vụ ngắn
        // executorService = Executors.newCachedThreadPool();
        executorService = Executors.newSingleThreadExecutor(); // Hoặc giữ single thread nếu muốn tuần tự
    }

    // --- Create Event---
    public void insert(Event event, OnEventOperationListener listener) {
        executorService.execute(() -> {
            // **QUAN TRỌNG: Quyền WRITE_CALENDAR phải được kiểm tra trước khi gọi**
            if (event.getCalendarId() <= 0) {
                // Tự động tìm kiếm lịch Google mặc định
                long googleCalendarId = findGoogleCalendar();
                if (googleCalendarId <= 0) {
                    Log.w(TAG, "Không tìm thấy lịch nào. Thử tạo calendar cục bộ...");
                    googleCalendarId = createLocalCalendar();
                    if (googleCalendarId <= 0) {
                        Log.e(TAG, "Không thể tạo calendar cục bộ");
                        handleError(listener, "Không thể tìm thấy hoặc tạo lịch. Vui lòng kiểm tra quyền truy cập và đăng nhập Google.", null);
                        return;
                    }
                }
                Log.d(TAG, "Tự động chọn Calendar: " + googleCalendarId);
                event.setCalendarId(googleCalendarId);
            }
            
            // Kiểm tra lịch tồn tại trước khi insert
            boolean calendarExists = checkCalendarExists(event.getCalendarId());
            if (!calendarExists) {
                Log.e(TAG, "Calendar ID " + event.getCalendarId() + " không tồn tại trong thiết bị!");
                if (listener != null) {
                    handleError(listener, "Lịch không tồn tại hoặc không có quyền truy cập.", null);
                    return;
                }
            } else {
                Log.d(TAG, "✓ Xác nhận lịch ID " + event.getCalendarId() + " tồn tại, tiếp tục insert");
            }
            
            if (TextUtils.isEmpty(event.getTimeZone())) {
                // Tự động đặt timezone mặc định nếu không có
                event.setTimeZone(TimeZone.getDefault().getID());
            }

            ContentValues values = new ContentValues();
            values.put(CalendarContract.Events.DTSTART, event.getStartTime());
            if (event.getEndTime() > event.getStartTime()) {
                values.put(CalendarContract.Events.DTEND, event.getEndTime());
                values.putNull(CalendarContract.Events.DURATION);
            } else {
                // Nếu là sự kiện cả ngày hoặc không có end time rõ ràng
                if(event.isAllDay()){
                    values.putNull(CalendarContract.Events.DTEND); // Đặt DTEND là null cho sự kiện cả ngày
                    values.put(CalendarContract.Events.DURATION, "P1D"); // Duration 1 ngày
                } else {
                    // Sự kiện tức thời (không có duration)
                    values.put(CalendarContract.Events.DTEND, event.getStartTime()); // Đặt end bằng start
                    values.putNull(CalendarContract.Events.DURATION);
                }
            }
            values.put(CalendarContract.Events.EVENT_TIMEZONE, event.getTimeZone());
            values.put(CalendarContract.Events.CALENDAR_ID, event.getCalendarId());
            values.put(CalendarContract.Events.TITLE, event.getTitle());
            values.put(CalendarContract.Events.DESCRIPTION, event.getDescription());
            values.put(CalendarContract.Events.EVENT_LOCATION, event.getLocation());
            values.put(CalendarContract.Events.ALL_DAY, event.isAllDay() ? 1 : 0);

            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            Log.d(TAG, "Inserting event: Title='" + event.getTitle() + 
                      "', Start=" + sdf.format(new Date(event.getStartTime())) + 
                      ", End=" + sdf.format(new Date(event.getEndTime())) + 
                      ", CalID=" + event.getCalendarId() +
                      ", Reminder=" + event.getReminderMinutes() + " minutes");

            try {
                Uri uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values);
                if (uri != null) {
                    long eventID = ContentUris.parseId(uri);
                    Log.d(TAG, "✓ Event inserted successfully with ID: " + eventID);
                    
                    // Thêm reminder nếu cần
                    if (event.getReminderMinutes() > 0) {
                        addReminder(eventID, event.getReminderMinutes());
                    }
                    
                    handleSuccess(listener, eventID);
                } else {
                    Log.e(TAG, "✗ Insert failed: ContentResolver returned null URI");
                    handleError(listener, "Insert failed: ContentResolver returned null URI", null);
                }
            } catch (SecurityException e) {
                Log.e(TAG, "✗ Insert failed: Permission denied", e);
                handleError(listener, "Insert failed: Permission denied", e);
            } catch (Exception e) {
                Log.e(TAG, "✗ Insert failed: " + e.getMessage(), e);
                handleError(listener, "Insert failed: Unexpected error", e);
            }
        });
    }

    // Thêm phương thức để tạo reminder cho event
    private void addReminder(long eventId, int minutes) {
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Reminders.EVENT_ID, eventId);
        values.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
        values.put(CalendarContract.Reminders.MINUTES, minutes);
        
        try {
            Uri uri = contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values);
            if (uri != null) {
                Log.d(TAG, "✓ Reminder added successfully for event ID: " + eventId + " (" + minutes + " minutes before)");
            } else {
                Log.e(TAG, "✗ Failed to add reminder for event ID: " + eventId);
            }
        } catch (Exception e) {
            Log.e(TAG, "✗ Error adding reminder: " + e.getMessage(), e);
        }
    }

    // Tạo một calendar cục bộ mới nếu không tìm thấy calendar nào
    private long createLocalCalendar() {
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Calendars.ACCOUNT_NAME, "LocalCalendar");
        values.put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL);
        values.put(CalendarContract.Calendars.NAME, "Lịch Cá Nhân");
        values.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Lịch Cá Nhân");
        values.put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER);
        values.put(CalendarContract.Calendars.OWNER_ACCOUNT, "LocalCalendar");
        values.put(CalendarContract.Calendars.VISIBLE, 1);
        values.put(CalendarContract.Calendars.SYNC_EVENTS, 1);
        values.put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().getID());
        
        Uri.Builder builder = CalendarContract.Calendars.CONTENT_URI.buildUpon();
        builder.appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true");
        builder.appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "LocalCalendar");
        builder.appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL);
        Uri calendarUri = builder.build();
        
        try {
            Uri uri = contentResolver.insert(calendarUri, values);
            if (uri != null) {
                long calendarId = ContentUris.parseId(uri);
                Log.d(TAG, "✓ Created local calendar with ID: " + calendarId);
                return calendarId;
            } else {
                Log.e(TAG, "✗ Failed to create local calendar: null URI returned");
            }
        } catch (Exception e) {
            Log.e(TAG, "✗ Error creating local calendar: " + e.getMessage(), e);
        }
        return -1;
    }

    // Phương thức để tìm lịch Google mặc định
    private long findGoogleCalendar() {
        long defaultCalendarId = -1;
        Cursor cursor = null;
        try {
            // Bước 1: Tìm bất kỳ lịch Google nào
            String selection = CalendarContract.Calendars.ACCOUNT_TYPE + " = ? AND " + 
                    CalendarContract.Calendars.SYNC_EVENTS + " = 1";
            String[] selectionArgs = new String[]{"com.google"}; // Google account type
            
            cursor = contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    CALENDAR_PROJECTION,
                    selection,
                    selectionArgs,
                    CalendarContract.Calendars.IS_PRIMARY + " DESC" // Ưu tiên lịch chính
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                defaultCalendarId = cursor.getLong(PROJECTION_CAL_ID_INDEX);
                String displayName = cursor.getString(PROJECTION_CAL_DISPLAY_NAME_INDEX);
                Log.d(TAG, "Found Google Calendar: ID=" + defaultCalendarId + ", Name=" + displayName);
                closeCursor(cursor);
                return defaultCalendarId;
            }
            
            closeCursor(cursor);
            
            // Bước 2: Nếu không tìm thấy lịch Google, tìm bất kỳ lịch nào khác
            cursor = contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    CALENDAR_PROJECTION,
                    CalendarContract.Calendars.SYNC_EVENTS + " = 1",
                    null,
                    CalendarContract.Calendars.IS_PRIMARY + " DESC" // Ưu tiên lịch chính
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                defaultCalendarId = cursor.getLong(PROJECTION_CAL_ID_INDEX);
                String displayName = cursor.getString(PROJECTION_CAL_DISPLAY_NAME_INDEX);
                Log.d(TAG, "Found non-Google Calendar as fallback: ID=" + defaultCalendarId + ", Name=" + displayName);
                closeCursor(cursor);
                return defaultCalendarId;
            }
            
            // Bước 3: Nếu vẫn không tìm thấy, sử dụng ID mặc định = 1 (lịch cục bộ)
            if (defaultCalendarId <= 0) {
                defaultCalendarId = 1; // Sử dụng calendar ID = 1 làm phương án cuối cùng
                Log.d(TAG, "No calendars found, using default ID = 1");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error finding default calendar", e);
        } finally {
            closeCursor(cursor);
        }
        return defaultCalendarId;
    }

    // Phương thức mới để kiểm tra calendar có tồn tại không
    private boolean checkCalendarExists(long calendarId) {
        if (calendarId <= 0) return false;
        
        Cursor cursor = null;
        try {
            Uri calendarUri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId);
            cursor = contentResolver.query(calendarUri, 
                    new String[]{ CalendarContract.Calendars._ID }, 
                    null, null, null);
            return cursor != null && cursor.moveToFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error checking calendar existence", e);
            return false;
        } finally {
            closeCursor(cursor);
        }
    }

    // --- Read Events ---

    // Lưu ý: LiveData này không tự cập nhật
    public LiveData<List<Event>> getAllEvents() {
        MutableLiveData<List<Event>> liveData = new MutableLiveData<>();
        executorService.execute(() -> {
            // **QUAN TRỌNG: Quyền READ_CALENDAR phải được kiểm tra trước khi gọi**
            Log.d(TAG, "Querying ALL events from ALL calendars");
            
            // In ra tất cả các calendar IDs có sẵn để debug
            Cursor calCursor = null;
            try {
                calCursor = contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    new String[] { CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME },
                    null, null, null);
                
                if (calCursor != null && calCursor.getCount() > 0) {
                    Log.d(TAG, "Found " + calCursor.getCount() + " calendars:");
                    while (calCursor.moveToNext()) {
                        long calId = calCursor.getLong(0);
                        String calName = calCursor.getString(1);
                        Log.d(TAG, "Calendar ID: " + calId + ", Name: " + calName);
                    }
                } else {
                    Log.w(TAG, "No calendars found in the device!");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error listing calendars", e);
            } finally {
                closeCursor(calCursor);
            }
            
            // Truy vấn tất cả sự kiện không có điều kiện lọc
            List<Event> events = queryEvents(null, null, CalendarContract.Events.DTSTART + " ASC");
            liveData.postValue(events);
        });
        return liveData;
    }

    // Lưu ý: LiveData này không tự cập nhật
    // **Đã sửa signature để nhận long id**
    public LiveData<Event> getEventById(long id) { // <<<< ĐÃ SỬA
        MutableLiveData<Event> liveData = new MutableLiveData<>();
        if (id <= 0) {
            Log.w(TAG, "getEventById called with invalid ID: " + id);
            liveData.postValue(null); // Trả về null nếu ID không hợp lệ
            return liveData;
        }
        executorService.execute(() -> {
            // **QUAN TRỌNG: Quyền READ_CALENDAR phải được kiểm tra trước khi gọi**
            Event event = null;
            // Sử dụng trực tiếp long id
            Uri eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id); // <<<< ĐÃ SỬA (bỏ ép kiểu)
            Cursor cursor = null;
            try {
                cursor = contentResolver.query(eventUri, EVENT_PROJECTION, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    event = cursorToEvent(cursor);
                } else {
                    Log.w(TAG,"Cursor null or empty for event ID: " + id);
                }
            } catch (SecurityException e) {
                Log.e(TAG, "getEventById failed: Permission denied for ID " + id, e);
            } catch (Exception e) {
                Log.e(TAG, "getEventById failed: Error querying event ID " + id, e);
            } finally {
                closeCursor(cursor);
            }
            liveData.postValue(event);
        });
        return liveData;
    }

    // Lưu ý: LiveData này không tự cập nhật
    public LiveData<List<Event>> getUpcomingEvents() {
        MutableLiveData<List<Event>> liveData = new MutableLiveData<>();
        executorService.execute(() -> {
            // **QUAN TRỌNG: Quyền READ_CALENDAR phải được kiểm tra trước khi gọi**
            long nowMillis = System.currentTimeMillis();
            String selection = CalendarContract.Events.DTSTART + " >= ?";
            String[] selectionArgs = new String[]{String.valueOf(nowMillis)};
            List<Event> events = queryEvents(selection, selectionArgs, CalendarContract.Events.DTSTART + " ASC");
            liveData.postValue(events);
        });
        return liveData;
    }

    // Lưu ý: LiveData này không tự cập nhật
    public LiveData<List<Event>> getEventsByMonth(String monthYear) { // Ví dụ monthYear = "03-2024"
        MutableLiveData<List<Event>> liveData = new MutableLiveData<>();
        executorService.execute(() -> {
            // **QUAN TRỌNG: Quyền READ_CALENDAR phải được kiểm tra trước khi gọi**
            long startMillis = 0;
            long endMillis = 0;
            try {
                String[] parts = monthYear.split("-");
                int month = Integer.parseInt(parts[0]); // 1-12
                int year = Integer.parseInt(parts[1]);

                Calendar calendar = Calendar.getInstance();
                calendar.set(Calendar.YEAR, year);
                calendar.set(Calendar.MONTH, month - 1); // Calendar.MONTH là 0-based
                calendar.set(Calendar.DAY_OF_MONTH, 1);
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0);
                startMillis = calendar.getTimeInMillis();

                calendar.add(Calendar.MONTH, 1); // Đi tới ngày đầu tiên của tháng sau
                endMillis = calendar.getTimeInMillis();

            } catch (Exception e) {
                Log.e(TAG, "Error parsing monthYear string: " + monthYear, e);
                liveData.postValue(new ArrayList<>()); // Trả list rỗng nếu lỗi parse
                return;
            }
            
            Log.d(TAG, "Fetching events for month " + monthYear + " (from " + new Date(startMillis) + " to " + new Date(endMillis) + ")");

            // Thử truy vấn tất cả sự kiện trước để xem có sự kiện nào không
            List<Event> allEvents = queryEvents(null, null, CalendarContract.Events.DTSTART + " ASC");
            
            if (!allEvents.isEmpty()) {
                Log.d(TAG, "Found " + allEvents.size() + " events in total across all calendars");
                // Lọc bằng Java thay vì SQL query
                List<Event> filteredEvents = new ArrayList<>();
                for (Event event : allEvents) {
                    long eventStart = event.getStartTime();
                    long eventEnd = event.getEndTime();
                    
                    if ((eventStart >= startMillis && eventStart < endMillis) || 
                        (eventStart < startMillis && eventEnd >= startMillis)) {
                        filteredEvents.add(event);
                        Log.d(TAG, "Manually filtered event: " + event.getTitle() + 
                                  " (ID:" + event.getId() + ", CalID:" + event.getCalendarId() + ")");
                    }
                }
                
                if (!filteredEvents.isEmpty()) {
                    Log.d(TAG, "Returning " + filteredEvents.size() + " filtered events for month " + monthYear);
                    liveData.postValue(filteredEvents);
                    return;
                } else {
                    Log.w(TAG, "No events found after manual filtering for month " + monthYear);
                }
            }

            // Nếu không tìm thấy sự kiện nào bằng phương pháp trên, thử query SQL
            String selection = "((" + CalendarContract.Events.DTSTART + " >= ? AND " + 
                               CalendarContract.Events.DTSTART + " < ?) OR " + // Bắt đầu trong tháng 
                               "(" + CalendarContract.Events.DTSTART + " < ? AND " + 
                               CalendarContract.Events.DTEND + " >= ?))"; // Bắt đầu trước, kết thúc trong/sau tháng
            
            String[] selectionArgs = new String[]{
                String.valueOf(startMillis), String.valueOf(endMillis), // Cho điều kiện 1
                String.valueOf(startMillis), String.valueOf(startMillis) // Cho điều kiện 2
            };
            
            List<Event> events = queryEvents(selection, selectionArgs, CalendarContract.Events.DTSTART + " ASC");
            if (!events.isEmpty()) {
                Log.d(TAG, "SQL query found " + events.size() + " events for month " + monthYear);
            }
            liveData.postValue(events);
        });
        return liveData;
    }

    // Lưu ý: LiveData này không tự cập nhật
    public LiveData<List<Event>> searchEvents(String query) {
        MutableLiveData<List<Event>> liveData = new MutableLiveData<>();
        executorService.execute(() -> {
            // **QUAN TRỌNG: Quyền READ_CALENDAR phải được kiểm tra trước khi gọi**
            String selection = CalendarContract.Events.TITLE + " LIKE ? OR " +
                    CalendarContract.Events.DESCRIPTION + " LIKE ? OR " +
                    CalendarContract.Events.EVENT_LOCATION + " LIKE ?";
            String queryArg = "%" + query + "%";
            String[] selectionArgs = new String[]{queryArg, queryArg, queryArg};
            List<Event> events = queryEvents(selection, selectionArgs, CalendarContract.Events.DTSTART + " ASC");
            liveData.postValue(events);
        });
        return liveData;
    }

    // --- Update Event ---
    public void update(Event event, OnEventOperationListener listener) {
        executorService.execute(() -> {
            // **QUAN TRỌNG: Quyền WRITE_CALENDAR phải được kiểm tra trước khi gọi**
            if (event.getId() <= 0) {
                handleError(listener, "Update failed: Invalid event ID (must be > 0)", null);
                return;
            }
            // Timezone thường không thay đổi, nhưng nên có nếu cần cập nhật
            if (TextUtils.isEmpty(event.getTimeZone())) {
                Log.w(TAG, "Update warning for event " + event.getId() + ": Missing timeZone, update might fail or use default.");
                // Consider fetching current timezone if needed, or default
                // event.setTimeZone(TimeZone.getDefault().getID()); // Không nên tự ý set ở đây
            }

            ContentValues values = new ContentValues();
            // Chỉ put những trường bạn muốn cho phép cập nhật
            values.put(CalendarContract.Events.DTSTART, event.getStartTime());
            if (event.getEndTime() > event.getStartTime()) {
                values.put(CalendarContract.Events.DTEND, event.getEndTime());
                values.putNull(CalendarContract.Events.DURATION);
            } else {
                if(event.isAllDay()){
                    values.putNull(CalendarContract.Events.DTEND);
                    values.put(CalendarContract.Events.DURATION, "P1D");
                } else {
                    values.put(CalendarContract.Events.DTEND, event.getStartTime());
                    values.putNull(CalendarContract.Events.DURATION);
                }
            }
            if(!TextUtils.isEmpty(event.getTimeZone())) { // Chỉ cập nhật nếu timezone hợp lệ
                values.put(CalendarContract.Events.EVENT_TIMEZONE, event.getTimeZone());
            }
            values.put(CalendarContract.Events.TITLE, event.getTitle());
            values.put(CalendarContract.Events.DESCRIPTION, event.getDescription());
            values.put(CalendarContract.Events.EVENT_LOCATION, event.getLocation());
            values.put(CalendarContract.Events.ALL_DAY, event.isAllDay() ? 1 : 0);
            // values.put(CalendarContract.Events.CALENDAR_ID, event.getCalendarId()); // Cập nhật calendar nếu cần

            Uri updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.getId());

            try {
                int rowsAffected = contentResolver.update(updateUri, values, null, null);
                Log.d(TAG, "Updated event ID: " + event.getId() + ", Rows affected: " + rowsAffected);
                
                if (rowsAffected > 0) {
                    // Cập nhật reminder nếu cần
                    updateReminder(event.getId(), event.getReminderMinutes());
                    
                    handleSuccess(listener, event.getId());
                } else {
                    // Có thể event không tồn tại hoặc không có gì thay đổi
                    handleError(listener, "Event not found or not updated (rowsAffected=0)", null);
                }
            } catch (SecurityException e) {
                handleError(listener, "Update failed: Permission denied", e);
            } catch (Exception e) {
                handleError(listener, "Update failed: Unexpected error", e);
            }
        });
    }
    
    // Phương thức cập nhật reminder cho event
    private void updateReminder(long eventId, int minutes) {
        try {
            // Trước tiên xóa tất cả reminders hiện có
            contentResolver.delete(
                CalendarContract.Reminders.CONTENT_URI,
                CalendarContract.Reminders.EVENT_ID + " = ?",
                new String[] {String.valueOf(eventId)}
            );
            
            // Sau đó thêm reminder mới nếu cần
            if (minutes > 0) {
                ContentValues values = new ContentValues();
                values.put(CalendarContract.Reminders.EVENT_ID, eventId);
                values.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
                values.put(CalendarContract.Reminders.MINUTES, minutes);
                
                Uri uri = contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values);
                if (uri != null) {
                    Log.d(TAG, "✓ Reminder updated successfully for event ID: " + eventId + " (" + minutes + " minutes before)");
                } else {
                    Log.e(TAG, "✗ Failed to update reminder for event ID: " + eventId);
                }
            } else {
                Log.d(TAG, "No reminder needed for event ID: " + eventId);
            }
        } catch (Exception e) {
            Log.e(TAG, "✗ Error updating reminder: " + e.getMessage(), e);
        }
    }

    // --- Delete Event ---
    public void delete(Event event, OnEventOperationListener listener) {
        executorService.execute(() -> {
            // **QUAN TRỌNG: Quyền WRITE_CALENDAR phải được kiểm tra trước khi gọi**
            if (event == null || event.getId() <= 0) {
                handleError(listener, "Delete failed: Invalid event or event ID", null);
                return;
            }

            Uri deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.getId());
            try {
                int rowsAffected = contentResolver.delete(deleteUri, null, null);
                Log.d(TAG, "Deleted event ID: " + event.getId() + ", Rows affected: " + rowsAffected);
                if (rowsAffected > 0) {
                    handleSuccess(listener, event.getId());
                } else {
                    // Có thể sự kiện đã bị xóa trước đó
                    handleError(listener, "Event not found or already deleted (rowsAffected=0)", null);
                }
            } catch (SecurityException e) {
                handleError(listener, "Delete failed: Permission denied", e);
            } catch (Exception e) {
                handleError(listener, "Delete failed: Unexpected error", e);
            }
        });
    }


    // --- Read Calendars ---
    public void getAvailableCalendars(OnCalendarsLoadedListener listener) {
        executorService.execute(() -> {
            // **QUAN TRỌNG: Quyền READ_CALENDAR phải được kiểm tra trước khi gọi**
            List<CalendarInfo> calendars = new ArrayList<>();
            Cursor cursor = null;
            Uri calendarsUri = CalendarContract.Calendars.CONTENT_URI;

            try {
                cursor = contentResolver.query(calendarsUri, CALENDAR_PROJECTION,
                        // Chỉ lấy những calendar có thể đồng bộ và hiển thị
                        CalendarContract.Calendars.SYNC_EVENTS + "=1 AND " + CalendarContract.Calendars.VISIBLE + "=1",
                        null,
                        CalendarContract.Calendars.IS_PRIMARY + " DESC, " + CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC"); // Ưu tiên primary

                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(PROJECTION_CAL_ID_INDEX);
                        String displayName = cursor.getString(PROJECTION_CAL_DISPLAY_NAME_INDEX);
                        String accountName = cursor.getString(PROJECTION_CAL_ACCOUNT_NAME_INDEX);
                        // IS_PRIMARY không đáng tin cậy hoàn toàn, nhưng có thể dùng làm gợi ý
                        boolean isPrimary = cursor.getInt(PROJECTION_CAL_IS_PRIMARY_INDEX) > 0;
                        // String ownerAccount = cursor.getString(PROJECTION_CAL_OWNER_ACCOUNT_INDEX);

                        // TODO: Kiểm tra thêm quyền ghi (CALENDAR_ACCESS_LEVEL) nếu cần thiết
                        // Chỉ thêm lịch mà người dùng có quyền ghi vào nếu muốn lọc chặt hơn

                        calendars.add(new CalendarInfo(id, displayName, accountName, isPrimary));
                        Log.d(TAG, "Found calendar: ID=" + id + ", Name=" + displayName + ", Account=" + accountName + ", isPrimary=" + isPrimary);
                    }
                    if (listener != null) listener.onSuccess(calendars);
                } else {
                    if (listener != null) listener.onError("Failed to query calendars (cursor is null)");
                }
            } catch (SecurityException e) {
                Log.e(TAG, "getAvailableCalendars failed: Permission denied", e);
                if (listener != null) listener.onError("Permission denied to read calendars");
            } catch (Exception e) {
                Log.e(TAG, "getAvailableCalendars failed: Error querying calendars", e);
                if (listener != null) listener.onError("Error getting calendars: " + e.getMessage());
            } finally {
                closeCursor(cursor);
            }
        });
    }


    // --- Helper method to query events ---
    private List<Event> queryEvents(String selection, String[] selectionArgs, String sortOrder) {
        List<Event> events = new ArrayList<>();
        Cursor cursor = null;
        // **QUAN TRỌNG: Quyền READ_CALENDAR phải được kiểm tra trước khi gọi hàm này**
        try {
            cursor = contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    EVENT_PROJECTION,
                    selection,
                    selectionArgs,
                    sortOrder
            );

            if (cursor != null) {
                int count = cursor.getCount();
                Log.d(TAG,"Query found " + count + " events.");
                
                if (count > 0) {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                    while (cursor.moveToNext()) {
                        Event event = cursorToEvent(cursor);
                        if (event != null) {
                            events.add(event);
                            // Log thông tin chi tiết về sự kiện
                            String startTime = sdf.format(new Date(event.getStartTime()));
                            String endTime = sdf.format(new Date(event.getEndTime()));
                            Log.d(TAG, "Event found: ID=" + event.getId() + 
                                      ", Title=" + event.getTitle() + 
                                      ", Start=" + startTime + 
                                      ", End=" + endTime +
                                      ", CalendarID=" + event.getCalendarId());
                        }
                    }
                } else {
                    Log.w(TAG, "No events found for query: " + (selection != null ? selection : "null"));
                    if (selectionArgs != null && selectionArgs.length > 0) {
                        StringBuilder argStr = new StringBuilder();
                        for (String arg : selectionArgs) {
                            argStr.append(arg).append(", ");
                        }
                        Log.d(TAG, "Selection args: " + argStr.toString());
                    }
                }
            } else {
                Log.w(TAG, "Event query returned null cursor.");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "queryEvents failed: Permission denied", e);
            // Trả về list rỗng hoặc throw exception tùy logic xử lý lỗi
        } catch (Exception e) {
            Log.e(TAG, "queryEvents failed: Error querying events", e);
        } finally {
            closeCursor(cursor);
        }
        return events;
    }

    // --- Helper method to convert Cursor row to Event object ---
    private Event cursorToEvent(@NonNull Cursor cursor) {
        try {
            long id = cursor.getLong(PROJECTION_ID_INDEX);
            long calendarId = cursor.getLong(PROJECTION_CALENDAR_ID_INDEX);
            String title = cursor.getString(PROJECTION_TITLE_INDEX);
            String location = cursor.getString(PROJECTION_LOCATION_INDEX);
            String description = cursor.getString(PROJECTION_DESCRIPTION_INDEX);
            long startTime = cursor.getLong(PROJECTION_DTSTART_INDEX);
            long endTime = cursor.getLong(PROJECTION_DTEND_INDEX); // Có thể là 0 hoặc null
            String duration = cursor.getString(PROJECTION_DURATION_INDEX); // Có thể null
            String timeZone = cursor.getString(PROJECTION_TIMEZONE_INDEX);
            boolean isAllDay = cursor.getInt(PROJECTION_ALL_DAY_INDEX) == 1;

            // Xử lý trường hợp endTime không có và có duration (phức tạp, bỏ qua xử lý duration chi tiết ở đây)
            if (endTime <= 0 && !TextUtils.isEmpty(duration)) {
                // Nếu là sự kiện cả ngày theo duration P1D, tính toán endTime hợp lý
                if(isAllDay && "P1D".equals(duration)) {
                    Calendar startCal = Calendar.getInstance();
                    if (!TextUtils.isEmpty(timeZone)) startCal.setTimeZone(TimeZone.getTimeZone(timeZone));
                    startCal.setTimeInMillis(startTime);
                    startCal.add(Calendar.DATE, 1); // Bắt đầu ngày hôm sau
                    endTime = startCal.getTimeInMillis();
                } else {
                    Log.w(TAG, "Event " + id + " has duration '" + duration + "' but no end time, endTime calculation not fully implemented.");
                    // Tạm thời đặt endTime = startTime nếu không phải all day P1D
                    if(!isAllDay) endTime = startTime;
                }
            } else if (endTime <= 0 && !isAllDay) {
                // Sự kiện tức thời nếu không có endTime/duration và không phải all day
                endTime = startTime;
            }
            // Nếu là all day nhưng không có endTime/duration, endTime cũng nên được tính toán
            else if (isAllDay && endTime <= 0) {
                Calendar startCal = Calendar.getInstance();
                if (!TextUtils.isEmpty(timeZone)) startCal.setTimeZone(TimeZone.getTimeZone(timeZone));
                startCal.setTimeInMillis(startTime);
                startCal.add(Calendar.DATE, 1); // Bắt đầu ngày hôm sau
                endTime = startCal.getTimeInMillis();
            }


            // Tạo đối tượng Event (Sử dụng constructor hoặc setters)
            Event event = new Event();
            event.setId(id);
            event.setCalendarId(calendarId);
            event.setTimeZone(timeZone);
            event.setTitle(title);
            event.setStartTime(startTime);
            event.setEndTime(endTime);
            event.setLocation(location);
            event.setDescription(description);
            event.setAllDay(isAllDay);
            
            // Đọc thông tin reminder
            int reminderMinutes = getReminderMinutes(id);
            event.setReminderMinutes(reminderMinutes);
            
            return event;
        } catch (Exception e) {
            Log.e(TAG, "Error converting cursor to event at position " + cursor.getPosition(), e);
            return null;
        }
    }

    // Phương thức lấy reminder minutes cho một event
    private int getReminderMinutes(long eventId) {
        int reminderMinutes = 0;
        Cursor cursor = null;
        
        try {
            cursor = contentResolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                new String[] {CalendarContract.Reminders.MINUTES},
                CalendarContract.Reminders.EVENT_ID + " = ?",
                new String[] {String.valueOf(eventId)},
                null
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                reminderMinutes = cursor.getInt(0);
                Log.d(TAG, "Found reminder for event " + eventId + ": " + reminderMinutes + " minutes");
            } else {
                Log.d(TAG, "No reminder found for event " + eventId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying reminder for event " + eventId, e);
        } finally {
            closeCursor(cursor);
        }
        
        return reminderMinutes;
    }

    // --- Helper method to safely close cursor ---
    private void closeCursor(Cursor cursor) {
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }
    }

    // --- Helper methods for handling listener callbacks ---
    private void handleSuccess(OnEventOperationListener listener, long id) {
        if (listener != null) {
            // Sẽ gọi verify sau khi thêm sự kiện thành công
            verifyRecentlyAddedEvent(id, 1); // Kiểm tra ID=1 vì đó là ID mặc định khi không tìm thấy Google Calendar
            
            // Có thể cần post lên main thread nếu listener cập nhật UI trực tiếp
            // Handler mainHandler = new Handler(Looper.getMainLooper());
            // mainHandler.post(() -> listener.onSuccess(id));
            listener.onSuccess(id);
        }
    }

    private void handleError(OnEventOperationListener listener, String message, Exception e) {
        Log.e(TAG, message, e); // Log lỗi kèm stack trace nếu có
        if (listener != null) {
            // Handler mainHandler = new Handler(Looper.getMainLooper());
            // mainHandler.post(() -> listener.onError(message));
            listener.onError(message);
        }
    }


    // --- Interfaces for callbacks ---

    // Listener cho các thao tác CRUD sự kiện
    // **Đã sửa onSuccess để nhận long id**
    public interface OnEventOperationListener {
        void onSuccess(long id); // <<<< ĐÃ SỬA
        void onError(String message);
    }

    // Listener cho việc load danh sách lịch
    public interface OnCalendarsLoadedListener {
        void onSuccess(List<CalendarInfo> calendars);
        void onError(String message);
    }

    // Phương thức để truy vấn sự kiện mới được thêm vào theo calendarId
    private void verifyRecentlyAddedEvent(long eventId, long calendarId) {
        executorService.execute(() -> {
            Log.d(TAG, "Verifying recently added event with ID: " + eventId + ", Calendar ID: " + calendarId);
            
            // 1. Kiểm tra sự kiện bằng ID
            if (eventId > 0) {
                Uri eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
                Cursor cursor = null;
                try {
                    cursor = contentResolver.query(eventUri, EVENT_PROJECTION, null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        Event event = cursorToEvent(cursor);
                        Log.d(TAG, "✓ Found event by ID: " + eventId + 
                             ", Title: " + event.getTitle() + 
                             ", CalendarID: " + event.getCalendarId());
                    } else {
                        Log.e(TAG, "✗ Event with ID " + eventId + " NOT found!");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error verifying event by ID", e);
                } finally {
                    closeCursor(cursor);
                }
            }
            
            // 2. Kiểm tra sự kiện bằng calendarId
            if (calendarId > 0) {
                Cursor cursor = null;
                try {
                    String selection = CalendarContract.Events.CALENDAR_ID + " = ?";
                    String[] selectionArgs = new String[]{String.valueOf(calendarId)};
                    
                    cursor = contentResolver.query(
                            CalendarContract.Events.CONTENT_URI,
                            EVENT_PROJECTION,
                            selection,
                            selectionArgs,
                            CalendarContract.Events.DTSTART + " DESC LIMIT 10"
                    );
                    
                    if (cursor != null && cursor.getCount() > 0) {
                        Log.d(TAG, "✓ Found " + cursor.getCount() + " events with Calendar ID: " + calendarId);
                        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                        while (cursor.moveToNext()) {
                            Event event = cursorToEvent(cursor);
                            String startTime = sdf.format(new Date(event.getStartTime()));
                            Log.d(TAG, "  Event: ID=" + event.getId() + 
                                      ", Title='" + event.getTitle() + 
                                      "', Start=" + startTime);
                        }
                    } else {
                        Log.e(TAG, "✗ NO events found with Calendar ID: " + calendarId);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error verifying events by Calendar ID", e);
                } finally {
                    closeCursor(cursor);
                }
            }
        });
    }
}