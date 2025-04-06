package com.example.personalschedule.viewmodels;

import android.app.Application;
import android.util.Log; // Thêm Log

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations; // Thêm Transformations

import com.example.personalschedule.database.EventRepository;
import com.example.personalschedule.models.Event;

public class EventDetailViewModel extends AndroidViewModel {

    private static final String TAG = "EventDetailVM";

    private final EventRepository repository;

    // Input: ID của event cần load (private MutableLiveData)
    private final MutableLiveData<Long> _eventId = new MutableLiveData<>();

    // Output: LiveData chứa thông tin Event, tự động cập nhật khi _eventId thay đổi
    private final LiveData<Event> event; // Không cần _ vì nó được expose trực tiếp

    // Output: LiveData cho kết quả xóa
    private final MutableLiveData<Boolean> _deleteResult = new MutableLiveData<>();
    public LiveData<Boolean> getDeleteResult() { return _deleteResult; } // Expose LiveData

    public EventDetailViewModel(@NonNull Application application) {
        super(application);
        repository = new EventRepository(application);

        // Sử dụng switchMap để liên kết event với _eventId
        // Khi _eventId thay đổi, switchMap sẽ gọi repository.getEventById(newId)
        // và trả về LiveData<Event> tương ứng.
        event = Transformations.switchMap(_eventId, id -> {
            if (id == null || id <= 0) {
                Log.w(TAG, "switchMap triggered with invalid ID: " + id);
                // Trả về LiveData chứa null nếu ID không hợp lệ
                MutableLiveData<Event> nullResult = new MutableLiveData<>();
                nullResult.setValue(null);
                return nullResult;
                // Hoặc có thể trả về LiveData rỗng nếu Repository hỗ trợ
                // return AbsentLiveData.create(); // Cần thêm dependency lifecycle-livedata-ktx
            }
            Log.d(TAG, "switchMap triggering repository.getEventById for ID: " + id);
            // Gọi phương thức repository đã cập nhật để nhận long
            return repository.getEventById(id); // <<<< GỌI REPO VỚI LONG ID
        });
    }

    /**
     * Kích hoạt việc load hoặc cập nhật dữ liệu cho một Event ID cụ thể.
     * @param id ID (long) của sự kiện cần load.
     */
    public void loadEvent(long id) {
        if (id <= 0) {
            Log.e(TAG,"Attempted to load event with invalid ID: " + id);
            // Không set _eventId nếu ID không hợp lệ để tránh trigger switchMap không cần thiết
            // Hoặc set _eventId thành giá trị đặc biệt nếu cần clear event hiện tại
            // _eventId.setValue(-1L); // Ví dụ
            return;
        }
        // Chỉ set giá trị nếu nó khác giá trị hiện tại để tránh trigger lại không cần thiết
        if (_eventId.getValue() == null || _eventId.getValue() != id) {
            Log.d(TAG,"Setting eventId LiveData to: " + id);
            _eventId.setValue(id);
        } else {
            Log.d(TAG,"loadEvent called with the same ID: " + id + ". No change triggered.");
        }
    }

    /**
     * Trả về LiveData chứa thông tin chi tiết của sự kiện.
     * Activity sẽ observe LiveData này.
     * @return LiveData<Event>
     */
    public LiveData<Event> getEvent() {
        return event;
    }

    /**
     * Yêu cầu xóa sự kiện được cung cấp.
     * @param eventToDelete Đối tượng Event cần xóa (phải có ID hợp lệ).
     */
    public void deleteEvent(@NonNull Event eventToDelete) {
        if (eventToDelete.getId() <= 0) {
            Log.e(TAG,"Attempted to delete event with invalid ID: " + eventToDelete.getId());
            _deleteResult.postValue(false); // Thông báo lỗi ngay lập tức
            return;
        }
        Log.d(TAG,"Requesting repository delete for event ID: " + eventToDelete.getId());
        repository.delete(eventToDelete, new EventRepository.OnEventOperationListener() {
            @Override
            public void onSuccess(long id) { // <<<< SỬA Ở ĐÂY: Nhận long id
                Log.d(TAG,"Repository delete success callback for ID: " + id);
                _deleteResult.postValue(true);
            }

            @Override
            public void onError(String message) {
                Log.e(TAG,"Repository delete error callback: " + message);
                _deleteResult.postValue(false);
            }
        });
    }

    // Không cần getDeleteSuccess() nữa vì đã expose _deleteResult qua getDeleteResult()

    // Reset trạng thái để tránh trigger lại khi có config change (nếu cần)
    public void resetDeleteResult() {
        _deleteResult.setValue(null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        Log.d(TAG,"onCleared called.");
        // Không cần remove observer cho switchMap, Lifecycle tự quản lý
    }
}