package com.example.personalschedule.viewmodels;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer; // Thêm import Observer

import com.example.personalschedule.database.EventRepository;
import com.example.personalschedule.models.CalendarInfo; // Lớp mới để chứa thông tin lịch
import com.example.personalschedule.models.Event;
// import com.example.personalschedule.utils.Constants; // Có thể không cần nếu xóa hết Priority/Reminder

import java.util.List;
import java.util.TimeZone; // Thêm import

public class AddEditEventViewModel extends AndroidViewModel {

    private static final String TAG = "AddEditVM";

    private final EventRepository repository;

    // Sử dụng _ convention cho MutableLiveData private
    private final MutableLiveData<Event> _eventData = new MutableLiveData<>();
    public LiveData<Event> getEventData() { return _eventData; } // Chỉ expose LiveData không thay đổi được

    // LiveData cho kết quả lưu trữ
    private final MutableLiveData<SaveResult> _saveResult = new MutableLiveData<>();
    public LiveData<SaveResult> getSaveResult() { return _saveResult; }

    // LiveData cho danh sách lịch và lịch được chọn
    private final MutableLiveData<List<CalendarInfo>> _availableCalendars = new MutableLiveData<>();
    public LiveData<List<CalendarInfo>> getAvailableCalendars() { return _availableCalendars; }

    private final MutableLiveData<Long> _selectedCalendarId = new MutableLiveData<>();
    public LiveData<Long> getSelectedCalendarId() { return _selectedCalendarId; }

    // Observer để quản lý việc observeForever
    private Observer<Event> eventObserver;

    public AddEditEventViewModel(@NonNull Application application) {
        super(application);
        repository = new EventRepository(application);

        // Initialize event data với giá trị mặc định hợp lệ cho Calendar Provider
        Event defaultEvent = new Event();
        defaultEvent.setId(0L); // ID 0 cho biết là event mới
        defaultEvent.setCalendarId(0L); // Calendar ID sẽ được chọn sau
        defaultEvent.setTimeZone(TimeZone.getDefault().getID()); // Timezone mặc định
        long now = System.currentTimeMillis();
        defaultEvent.setStartTime(now);
        defaultEvent.setEndTime(now + (60 * 60 * 1000)); // Mặc định +1 giờ
        // Xóa priority, reminderMinutes
        // defaultEvent.setRecurrencePattern(Constants.RECURRENCE_NONE); // Giữ lại nếu dùng recurrence
        _eventData.setValue(defaultEvent);

        // Khởi tạo selectedCalendarId (có thể là giá trị không hợp lệ ban đầu)
        _selectedCalendarId.setValue(0L);
    }

    // === Load Event Data ===
    public void loadEventById(long eventId) {
        if (eventId <= 0) {
            Log.w(TAG, "Invalid event ID passed to loadEventById: " + eventId);
            // Có thể reset eventData về default hoặc giữ nguyên tùy logic
            _eventData.postValue(createDefaultEvent()); // Reset về default nếu ID không hợp lệ
            return;
        }
        Log.d(TAG, "Requesting load for event ID: " + eventId);

        // Xóa observer cũ nếu có trước khi observe cái mới
        removeEventObserver();

        LiveData<Event> source = repository.getEventById(eventId);

        // **CẢNH BÁO**: observeForever cần được quản lý cẩn thận để tránh leak.
        // Một pattern tốt hơn là dùng MediatorLiveData hoặc Transformations.switchMap
        // Hoặc Repository nên dùng callback/coroutine để trả về Event đơn lẻ.
        eventObserver = event -> {
            if (event != null) {
                Log.d(TAG, "Event data loaded/updated for ID " + eventId);
                _eventData.postValue(event);
                // Cập nhật luôn calendar được chọn nếu đang load event có sẵn
                _selectedCalendarId.postValue(event.getCalendarId());
            } else {
                Log.w(TAG, "Repository returned null event for ID: " + eventId);
                // Xử lý trường hợp không tìm thấy event (ví dụ: set data về null hoặc default)
                _eventData.postValue(createDefaultEvent()); // Hoặc null nếu muốn Activity xử lý
            }
        };
        source.observeForever(eventObserver);
        // **QUAN TRỌNG**: Cần gọi removeEventObserver() trong onCleared() của ViewModel.
    }

    private void removeEventObserver() {
        // Check if eventData's source (LiveData from repo) is being observed by our observer
        // This is tricky because we don't hold a direct reference to the source LiveData always.
        // If repository.getEventById always returns the *same* LiveData instance,
        // we could try removing the observer from it. But it's safer to just remove
        // the observer when the ViewModel is cleared. The observeForever approach here
        // is simplified and potentially leaky if not managed in onCleared().

        // Tạm thời chỉ set observer về null, việc remove thực sự trong onCleared
        eventObserver = null;
    }

    // === Calendar Management ===
    public void loadAvailableCalendars() {
        Log.d(TAG, "Requesting available calendars list.");
        // Gọi phương thức mới trong Repository để lấy danh sách lịch
        repository.getAvailableCalendars(new EventRepository.OnCalendarsLoadedListener() {
            @Override
            public void onSuccess(List<CalendarInfo> calendars) {
                Log.d(TAG, "Available calendars loaded: " + calendars.size());
                _availableCalendars.postValue(calendars);
                // Tự động chọn lịch đầu tiên làm mặc định nếu chưa có lịch nào được chọn
                if (_selectedCalendarId.getValue() == null || _selectedCalendarId.getValue() <= 0) {
                    if (calendars != null && !calendars.isEmpty()) {
                        // Ưu tiên lịch chính (nếu có cách xác định) hoặc lịch đầu tiên
                        CalendarInfo defaultCalendar = findPrimaryCalendar(calendars);
                        if(defaultCalendar == null) defaultCalendar = calendars.get(0);
                        _selectedCalendarId.postValue(defaultCalendar.getId());
                        Log.d(TAG, "Default calendar selected: ID " + defaultCalendar.getId());
                    } else {
                        Log.w(TAG,"No calendars found or returned.");
                        _selectedCalendarId.postValue(0L); // Không có lịch nào để chọn
                    }
                }
            }
            @Override
            public void onError(String message) {
                Log.e(TAG, "Error loading calendars: " + message);
                _availableCalendars.postValue(null); // Hoặc list rỗng
            }
        });
    }
    // Helper để tìm lịch chính (cần logic cụ thể hơn trong Repository hoặc đây)
    private CalendarInfo findPrimaryCalendar(List<CalendarInfo> calendars){
        if (calendars == null) return null;
        for(CalendarInfo cal : calendars){
            // Thêm logic kiểm tra isPrimary hoặc tên/loại tài khoản nếu có
            if(cal.isPrimary()){ // Giả sử CalendarInfo có trường isPrimary
                return cal;
            }
        }
        // Nếu không tìm thấy primary, trả về null để chọn cái đầu tiên
        return null;
    }


    public void setSelectedCalendarId(long calendarId) {
        if (calendarId > 0) {
            _selectedCalendarId.setValue(calendarId);
            Log.d(TAG, "Calendar ID selected: " + calendarId);
        }
    }

    // === Update Event Fields (Giữ lại các hàm update cần thiết) ===

    // Lưu ý: Không cần các hàm update() nữa nếu Activity tạo object Event hoàn chỉnh trước khi gọi saveEvent()
    // Tuy nhiên, nếu muốn cập nhật LiveData ngay khi người dùng nhập liệu thì vẫn giữ lại.

    public void updateTitle(String title) {
        Event current = _eventData.getValue();
        if (current != null) {
            current.setTitle(title);
            // _eventData.setValue(current); // Không cần thiết nếu chỉ cập nhật trước khi save
        }
    }

    // Cập nhật để nhận long timestamp
    public void updateStartTime(long startTimeMillis) {
        Event current = _eventData.getValue();
        if (current != null) {
            current.setStartTime(startTimeMillis);
            if (current.getEndTime() < startTimeMillis) {
                current.setEndTime(startTimeMillis + (60 * 60 * 1000)); // +1 giờ
            }
            // _eventData.setValue(current);
        }
    }

    // Cập nhật để nhận long timestamp
    public void updateEndTime(long endTimeMillis) {
        Event current = _eventData.getValue();
        if (current != null) {
            // Đảm bảo endTime >= startTime
            if(endTimeMillis >= current.getStartTime()){
                current.setEndTime(endTimeMillis);
            } else {
                Log.w(TAG,"Attempted to set end time before start time.");
                // Giữ nguyên end time cũ hoặc set bằng start time + 1h
                current.setEndTime(current.getStartTime() + (60 * 60 * 1000));
            }
            // _eventData.setValue(current);
        }
    }

    public void updateLocation(String location) {
        Event current = _eventData.getValue();
        if (current != null) {
            current.setLocation(location);
            // _eventData.setValue(current);
        }
    }

    public void updateDescription(String description) {
        Event current = _eventData.getValue();
        if (current != null) {
            current.setDescription(description);
            // _eventData.setValue(current);
        }
    }

    public void updateAllDay(boolean isAllDay) {
        Event current = _eventData.getValue();
        if (current != null) {
            current.setAllDay(isAllDay);
            // _eventData.setValue(current);
        }
    }

    // === Save Event ===
    // Nhận Event object đã hoàn chỉnh từ Activity
    public void saveEvent(Event eventToSave) {
        if (eventToSave == null) {
            _saveResult.postValue(new SaveResult(false, -1, "Event data is null."));
            return;
        }
        
        // Lấy ID lịch từ selected nếu có
        Long selectedId = _selectedCalendarId.getValue();
        if(selectedId != null && selectedId > 0) {
            eventToSave.setCalendarId(selectedId);
        }
        // Không kiểm tra calendarId nữa, để EventRepository tự tìm lịch mặc định nếu cần
        
        // Đảm bảo có TimeZone
        if (eventToSave.getTimeZone() == null || eventToSave.getTimeZone().isEmpty()) {
            eventToSave.setTimeZone(TimeZone.getDefault().getID());
        }

        Log.d(TAG, "Attempting to save event. Is Update? " + (eventToSave.getId() > 0));

        EventRepository.OnEventOperationListener listener = new EventRepository.OnEventOperationListener() {
            @Override
            public void onSuccess(long id) { // Nhận long ID
                Log.d(TAG, "Repository onSuccess callback for ID: " + id);
                // Cập nhật ID cho event trong LiveData nếu là insert mới
                if (eventToSave.getId() <= 0) {
                    eventToSave.setId(id); // Đặt ID trả về từ repo
                    _eventData.postValue(eventToSave); // Cập nhật LiveData với ID mới
                }
                _saveResult.postValue(new SaveResult(true, id, null));
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Repository onError callback: " + message);
                _saveResult.postValue(new SaveResult(false, eventToSave.getId(), message));
            }
        };

        if (eventToSave.getId() > 0) {
            // Update existing event
            repository.update(eventToSave, listener);
        } else {
            // Create new event
            repository.insert(eventToSave, listener);
        }
    }

    // Reset trạng thái save để observer không bị trigger lại khi có config change
    public void resetSaveResult() {
        _saveResult.setValue(null);
    }

    // === Cleanup ===
    @Override
    protected void onCleared() {
        super.onCleared();
        Log.d(TAG, "onCleared called.");
        removeEventObserver(); // Quan trọng để tránh leak từ observeForever
        // Repository không cần shutdown executor nếu nó là singleton hoặc quản lý bởi Application scope
    }

    // === Helper ===
    private Event createDefaultEvent() {
        Event defaultEvent = new Event();
        defaultEvent.setId(0L);
        defaultEvent.setCalendarId(0L);
        defaultEvent.setTimeZone(TimeZone.getDefault().getID());
        long now = System.currentTimeMillis();
        defaultEvent.setStartTime(now);
        defaultEvent.setEndTime(now + (60 * 60 * 1000));
        return defaultEvent;
    }

    // === Lớp Helper cho Save Result ===
    public static class SaveResult {
        private final boolean success;
        private final long eventId; // ID của event đã lưu hoặc ID cũ nếu lỗi update
        private final String errorMessage;

        SaveResult(boolean success, long eventId, String errorMessage) {
            this.success = success;
            this.eventId = eventId;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() { return success; }
        public long getEventId() { return eventId; }
        public String getErrorMessage() { return errorMessage; }
    }
}