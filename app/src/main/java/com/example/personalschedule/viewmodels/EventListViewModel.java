package com.example.personalschedule.viewmodels;

import android.app.Application;
import android.text.TextUtils; // Thêm import TextUtils
import android.util.Log; // Thêm Log

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.personalschedule.database.EventRepository;
import com.example.personalschedule.models.Event;

import java.util.Collections; // Thêm import Collections
import java.util.List;

public class EventListViewModel extends AndroidViewModel {

    private static final String TAG = "EventListVM";

    private final EventRepository repository;

    // Input: Từ khóa tìm kiếm (private MutableLiveData)
    private final MutableLiveData<String> _searchQuery = new MutableLiveData<>();

    // Output: Danh sách sự kiện dựa trên tìm kiếm (hoặc tất cả nếu query rỗng)
    private final LiveData<List<Event>> searchResults;

    // Output: Kết quả của thao tác xóa
    private final MutableLiveData<DeleteResult> _deleteResult = new MutableLiveData<>();
    public LiveData<DeleteResult> getDeleteResult() { return _deleteResult; }

    public EventListViewModel(@NonNull Application application) {
        super(application);
        repository = new EventRepository(application);

        // Khởi tạo giá trị ban đầu cho searchQuery (ví dụ: null hoặc rỗng để load tất cả ban đầu)
        _searchQuery.setValue(null);

        // Sử dụng switchMap để searchResults phụ thuộc vào _searchQuery
        searchResults = Transformations.switchMap(_searchQuery, query -> {
            if (TextUtils.isEmpty(query)) {
                Log.d(TAG, "Search query is empty, loading all events.");
                // Trả về LiveData chứa tất cả sự kiện
                return repository.getAllEvents();
            } else {
                Log.d(TAG, "Search query changed: '" + query + "', searching events.");
                // Trả về LiveData chứa kết quả tìm kiếm
                return repository.searchEvents(query);
            }
            // Lưu ý: LiveData trả về từ repository hiện tại chỉ load 1 lần.
            // Nếu muốn tự động refresh khi Calendar Provider thay đổi, cần cơ chế phức tạp hơn.
        });
    }

    // --- Lấy dữ liệu danh sách ---

    /**
     * Trả về LiveData chứa danh sách sự kiện dựa trên query tìm kiếm hiện tại.
     * Nếu query rỗng, sẽ trả về tất cả sự kiện.
     * Activity/Fragment nên observe LiveData này để hiển thị danh sách.
     */
    public LiveData<List<Event>> getEventList() {
        return searchResults;
    }

    /**
     * Lấy LiveData chứa các sự kiện sắp tới.
     * Lưu ý: LiveData này chỉ được cập nhật khi hàm này được gọi.
     */
    public LiveData<List<Event>> getUpcomingEvents() {
        Log.d(TAG, "Requesting upcoming events.");
        return repository.getUpcomingEvents();
    }

    /**
     * Lấy LiveData chứa các sự kiện trong một tháng cụ thể.
     * Lưu ý: LiveData này chỉ được cập nhật khi hàm này được gọi.
     * @param monthYear Chuỗi định dạng "MM-yyyy" (ví dụ: "03-2024").
     */
    public LiveData<List<Event>> getEventsByMonth(String monthYear) {
        Log.d(TAG, "Requesting events for month: " + monthYear);
        if (TextUtils.isEmpty(monthYear)) {
            // Trả về LiveData rỗng nếu monthYear không hợp lệ
            MutableLiveData<List<Event>> emptyResult = new MutableLiveData<>();
            emptyResult.setValue(Collections.emptyList());
            return emptyResult;
        }
        return repository.getEventsByMonth(monthYear);
    }

    // --- Tìm kiếm ---

    /**
     * Thiết lập từ khóa tìm kiếm. Thay đổi này sẽ trigger `searchResults` cập nhật.
     * @param query Từ khóa tìm kiếm. Set null hoặc rỗng để hiển thị tất cả.
     */
    public void setSearchQuery(String query) {
        // Sử dụng postValue nếu có thể gọi từ background thread, setValue nếu chắc chắn trên main thread
        _searchQuery.setValue(query);
    }

    /**
     * Lấy giá trị query hiện tại.
     */
    public String getCurrentSearchQuery() {
        return _searchQuery.getValue();
    }


    // --- Xóa sự kiện ---

    /**
     * Yêu cầu xóa một sự kiện.
     * @param event Sự kiện cần xóa (phải có ID hợp lệ).
     */
    public void deleteEvent(Event event) {
        if (event == null || event.getId() <= 0) {
            Log.e(TAG, "Attempted to delete invalid event.");
            _deleteResult.postValue(new DeleteResult(false, -1, "Invalid event data"));
            return;
        }
        Log.d(TAG, "Requesting repository delete for event ID: " + event.getId());
        repository.delete(event, new EventRepository.OnEventOperationListener() {
            @Override
            public void onSuccess(long id) { // <<<< SỬA Ở ĐÂY: Nhận long
                Log.d(TAG, "Repository delete success callback for ID: " + id);
                _deleteResult.postValue(new DeleteResult(true, id, null));
                // Trigger refresh danh sách hiện tại sau khi xóa thành công
                refreshCurrentList();
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Repository delete error callback: " + message);
                _deleteResult.postValue(new DeleteResult(false, event.getId(), message));
            }
        });
    }

    /**
     * Làm mới danh sách sự kiện hiện tại bằng cách trigger lại `searchQuery`.
     */
    public void refreshCurrentList() {
        Log.d(TAG, "Refreshing current event list...");
        // Set lại giá trị hiện tại của searchQuery để trigger switchMap load lại
        _searchQuery.setValue(_searchQuery.getValue());
        // Nếu _searchQuery đang là null, set nó về null một lần nữa cũng sẽ trigger
        // if (_searchQuery.getValue() == null) {
        //     _searchQuery.setValue(null);
        // }
    }

    // Reset trạng thái delete để tránh trigger lại không cần thiết
    public void resetDeleteResult() {
        _deleteResult.setValue(null);
    }


    // === Lớp Helper cho Delete Result ===
    public static class DeleteResult {
        public final boolean success;
        public final long eventId; // ID của event đã xóa hoặc ID cũ nếu lỗi
        public final String errorMessage;

        DeleteResult(boolean success, long eventId, String errorMessage) {
            this.success = success;
            this.eventId = eventId;
            this.errorMessage = errorMessage;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        Log.d(TAG,"onCleared called.");
        // Repository không cần shutdown nếu quản lý bởi Application scope
    }
}