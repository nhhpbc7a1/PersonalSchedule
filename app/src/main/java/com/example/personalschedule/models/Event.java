package com.example.personalschedule.models;

// Không còn là Entity của Room
// import androidx.room.Entity;
// import androidx.room.PrimaryKey;
// import androidx.room.TypeConverters;

// import com.example.personalschedule.utils.DateTimeConverter; // Không cần nếu không dùng Date nữa

// Không cần TypeConverter nữa
// @TypeConverters(DateTimeConverter.class)
public class Event {

    // @PrimaryKey(autoGenerate = true) // Xóa annotation
    private long id; // Thay int thành long để khớp với CalendarContract.Events._ID

    private long calendarId; // THÊM: ID của lịch hệ thống mà sự kiện thuộc về
    private String timeZone; // THÊM: Timezone của sự kiện (bắt buộc cho Calendar Provider)

    private String title;
    // private Date startTime; // Thay Date bằng long (milliseconds UTC)
    private long startTime;
    // private Date endTime; // Thay Date bằng long (milliseconds UTC)
    private long endTime;
    private String location;
    private String description;
    private int reminderMinutes; // Thêm lại: Lời nhắc trước sự kiện (phút)
    // priority đã được xóa hoàn toàn

    private boolean isAllDay; // Giữ lại, tương ứng với CalendarContract.Events.ALL_DAY

    // Constructors
    public Event() {
        // Khởi tạo giá trị mặc định nếu cần
        this.id = 0; // ID=0 thường có nghĩa là chưa được lưu vào Calendar Provider
        this.calendarId = 0; // Cần được set trước khi insert
        this.timeZone = java.util.TimeZone.getDefault().getID(); // Có thể lấy timezone mặc định
        this.startTime = System.currentTimeMillis();
        this.endTime = this.startTime; // Mặc định thời gian kết thúc bằng bắt đầu
        this.reminderMinutes = 15; // Mặc định nhắc trước 15 phút
    }

    // Constructor cập nhật với các trường mới/thay đổi
    public Event(long id, long calendarId, String timeZone, String title, long startTime, long endTime, String location,
                 String description, int reminderMinutes, boolean isAllDay) {
        this.id = id;
        this.calendarId = calendarId;
        this.timeZone = (timeZone != null && !timeZone.isEmpty()) ? timeZone : java.util.TimeZone.getDefault().getID(); // Đảm bảo có timezone
        this.title = title;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
        this.description = description;
        this.reminderMinutes = reminderMinutes;
        this.isAllDay = isAllDay;
    }

    // --- Getters and Setters ---
    // Đã cập nhật kiểu dữ liệu và thêm/xóa các trường cần thiết

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getCalendarId() {
        return calendarId;
    }

    public void setCalendarId(long calendarId) {
        this.calendarId = calendarId;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    // Getter/Setter cho reminderMinutes
    public int getReminderMinutes() {
        return reminderMinutes;
    }

    public void setReminderMinutes(int reminderMinutes) {
        this.reminderMinutes = reminderMinutes;
    }

    public boolean isAllDay() {
        return isAllDay;
    }

    public void setAllDay(boolean allDay) {
        isAllDay = allDay;
    }

    @Override
    public String toString() {
        return "Event{" +
                "id=" + id +
                ", calendarId=" + calendarId +
                ", timeZone='" + timeZone + '\'' +
                ", title='" + title + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", location='" + location + '\'' +
                ", description='" + description + '\'' +
                ", reminderMinutes=" + reminderMinutes +
                ", isAllDay=" + isAllDay +
                '}';
    }
}