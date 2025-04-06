package com.example.personalschedule.adapters;

import android.content.Context;
import android.text.TextUtils; // Thêm import TextUtils
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
// import android.widget.ImageView; // Xóa import ImageView nếu không dùng nữa
import android.widget.TextView;

import androidx.annotation.NonNull;
// import androidx.core.content.ContextCompat; // Xóa import không dùng nữa
import androidx.recyclerview.widget.RecyclerView;

import com.example.personalschedule.R;
import com.example.personalschedule.models.Event;
// import com.example.personalschedule.utils.Constants; // Có thể không cần nữa
import com.example.personalschedule.utils.DateTimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale; // Thêm Locale cho String.format

public class EventAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<Object> items = new ArrayList<>(); // Chứa cả Header (String) và Event
    private OnEventClickListener listener;
    private Context context; // Giữ context nếu cần cho resource hoặc việc khác
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_EVENT = 1;

    public EventAdapter(Context context, OnEventClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_header, parent, false);
            return new HeaderViewHolder(view);
        } else { // TYPE_EVENT
            View view = inflater.inflate(R.layout.item_event, parent, false);
            return new EventViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_HEADER) {
            ((HeaderViewHolder) holder).bind((String) items.get(position));
        } else { // TYPE_EVENT
            ((EventViewHolder) holder).bind((Event) items.get(position));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (position < 0 || position >= items.size()) {
            return -1; // Hoặc xử lý lỗi khác
        }
        // instanceof là cách an toàn để kiểm tra kiểu
        return items.get(position) instanceof String ? TYPE_HEADER : TYPE_EVENT;
    }

    /**
     * Cập nhật danh sách sự kiện hiển thị, có thể bao gồm các tiêu đề nhóm.
     * Cần một logic rõ ràng hơn để nhóm sự kiện (ví dụ: theo ngày, theo trạng thái).
     * Phiên bản này đơn giản hóa, chỉ hiển thị tất cả sự kiện dưới 1 tiêu đề nếu có.
     * Bạn cần điều chỉnh logic này cho phù hợp với yêu cầu nhóm thực tế.
     * @param events Danh sách các sự kiện chính cần hiển thị.
     */
    public void submitList(List<Event> events) {
        items.clear();
        if (events != null && !events.isEmpty()) {
            // TODO: Thêm logic nhóm sự kiện ở đây nếu cần
            // Ví dụ: Thêm header "Tất cả sự kiện" hoặc nhóm theo ngày
            items.add("Tất cả sự kiện"); // Ví dụ header đơn giản
            items.addAll(events);
        }
        // Luôn gọi notifyDataSetChanged sau khi thay đổi dữ liệu hoàn toàn
        // Cân nhắc dùng DiffUtil để cải thiện hiệu năng nếu danh sách lớn và thay đổi thường xuyên
        notifyDataSetChanged();
    }

    // Xóa phương thức setEvents cũ nếu không còn dùng logic nhóm phức tạp đó
    /*
    public void setEvents(List<Event> events, List<Event> selectedDayEvents) {
        items.clear();
        // ... logic cũ ...
        notifyDataSetChanged();
    }
    */

    // --- ViewHolders ---

    public class EventViewHolder extends RecyclerView.ViewHolder {
        private TextView tvDate;
        private TextView tvDayOfWeek;
        private TextView tvTitle;
        private TextView tvTime;
        private TextView tvLocation;
        // private ImageView ivPriority; // XÓA BIẾN NÀY

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tv_date);
            tvDayOfWeek = itemView.findViewById(R.id.tv_day_of_week);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvLocation = itemView.findViewById(R.id.tv_location);
            // ivPriority = itemView.findViewById(R.id.iv_priority); // XÓA DÒNG NÀY
            // TODO: Xóa ImageView với id 'iv_priority' khỏi file layout res/layout/item_event.xml

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition(); // Sử dụng getAdapterPosition() thay vì getBindingAdapterPosition()
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    Object item = items.get(position);
                    if (item instanceof Event) { // Kiểm tra lại kiểu trước khi cast
                        listener.onEventClick((Event) item);
                    }
                }
            });
        }

        public void bind(Event event) {
            if (event == null) return; // Tránh NullPointerException

            // Sử dụng các phương thức DateTimeUtils nhận long
            tvDate.setText(DateTimeUtils.formatDate(event.getStartTime()));
            tvDayOfWeek.setText(DateTimeUtils.formatDayOfWeek(event.getStartTime()));
            tvTitle.setText(event.getTitle());

            if (event.isAllDay()) {
                tvTime.setText(R.string.all_day); // Sử dụng resource string
            } else {
                // Đảm bảo endTime hợp lệ trước khi format
                String endTimeFormatted = (event.getEndTime() >= event.getStartTime())
                        ? DateTimeUtils.formatTime(event.getEndTime())
                        : "?";
                // Sử dụng Locale.getDefault() cho String.format
                tvTime.setText(String.format(Locale.getDefault(), "%s - %s",
                        DateTimeUtils.formatTime(event.getStartTime()),
                        endTimeFormatted));
            }

            // Kiểm tra location bằng TextUtils
            if (!TextUtils.isEmpty(event.getLocation())) {
                tvLocation.setVisibility(View.VISIBLE);
                tvLocation.setText(event.getLocation());
            } else {
                tvLocation.setVisibility(View.GONE);
            }

            // Cập nhật background dựa trên isToday (sử dụng long)
            if (DateTimeUtils.isToday(event.getStartTime())) {
                itemView.setBackgroundResource(R.drawable.bg_event_item_today); // Đảm bảo drawable tồn tại
            } else {
                itemView.setBackgroundResource(R.drawable.bg_event_item); // Đảm bảo drawable tồn tại
            }
        }
    }

    public class HeaderViewHolder extends RecyclerView.ViewHolder {
        private TextView tvHeader;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvHeader = itemView.findViewById(R.id.tv_header); // Đảm bảo ID đúng trong item_header.xml
        }

        public void bind(String headerText) {
            tvHeader.setText(headerText);
        }
    }

    // --- Interface ---
    public interface OnEventClickListener {
        void onEventClick(Event event);
    }
}