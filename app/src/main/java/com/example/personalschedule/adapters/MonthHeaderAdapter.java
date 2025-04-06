package com.example.personalschedule.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.personalschedule.R;
import com.example.personalschedule.models.Event;
import com.example.personalschedule.models.MonthHeader;
import com.example.personalschedule.utils.Constants;
import com.example.personalschedule.utils.DateTimeUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MonthHeaderAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_MONTH_HEADER = 0;
    private static final int TYPE_EVENT = 1;
    private static final int TYPE_EMPTY = 2;
    private static final int TYPE_TITLE = 3; //Thêm type

    private Context context;
    private List<Object> items = new ArrayList<>();
    private EventAdapter.OnEventClickListener eventClickListener;

    public MonthHeaderAdapter(Context context, EventAdapter.OnEventClickListener eventClickListener) {
        this.context = context;
        this.eventClickListener = eventClickListener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_MONTH_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_month_header, parent, false);
            return new MonthHeaderViewHolder(view);
        } else if (viewType == TYPE_EVENT) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_event, parent, false);
            return new EventViewHolder(view,context, eventClickListener); // Truyền context và listener
        }else if(viewType == TYPE_TITLE){
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_title, parent, false);
            return new TitleViewHolder(view);
        }
        else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_empty_month, parent, false);
            return new EmptyViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof MonthHeaderViewHolder) {
            MonthHeader monthHeader = (MonthHeader) items.get(position);
            ((MonthHeaderViewHolder) holder).bind(monthHeader);
        } else if (holder instanceof EventViewHolder) {
            Event event = (Event) items.get(position);
            ((EventViewHolder) holder).bind(event);
            holder.itemView.setOnClickListener(v -> {
                if (eventClickListener != null) {
                    eventClickListener.onEventClick(event);
                }
            });
        }else if (holder instanceof TitleViewHolder) {
            String title = (String) items.get(position);
            ((TitleViewHolder) holder).bind(title);
        }
        else if (holder instanceof EmptyViewHolder) {
            MonthHeader monthHeader = (MonthHeader) items.get(position - 1);
            ((EmptyViewHolder) holder).bind(monthHeader);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (items.get(position) instanceof MonthHeader) {
            return TYPE_MONTH_HEADER;
        } else if (items.get(position) instanceof Event) {
            return TYPE_EVENT;
        }else if(items.get(position) instanceof String){
            return TYPE_TITLE;
        }
        else {
            return TYPE_EMPTY;
        }
    }

    public void setEvents(List<Object> events) {
        if (events == null || events.isEmpty()) {
            MonthHeader currentMonth = getCurrentMonth();
            items.clear();
            items.add(currentMonth);
            items.add("EMPTY");
            notifyDataSetChanged();
            return;
        }

        items.clear();
        items.addAll(events);


        notifyDataSetChanged();
    }


    private MonthHeader getCurrentMonth() {
        Calendar cal = Calendar.getInstance();
        return new MonthHeader(cal.get(Calendar.MONTH), cal.get(Calendar.YEAR));
    }

    static class MonthHeaderViewHolder extends RecyclerView.ViewHolder {
        private TextView tvMonthYear;

        public MonthHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMonthYear = itemView.findViewById(R.id.tv_month_year);
        }

        public void bind(MonthHeader monthHeader) {
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.MONTH, monthHeader.getMonth());
            calendar.set(Calendar.YEAR, monthHeader.getYear());

            tvMonthYear.setText(DateTimeUtils.formatMonthYear(calendar.getTime()));
        }
    }

    static class EmptyViewHolder extends RecyclerView.ViewHolder {
        private TextView tvEmptyMessage;

        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEmptyMessage = itemView.findViewById(R.id.tv_empty_message);
        }

        public void bind(MonthHeader monthHeader) {
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.MONTH, monthHeader.getMonth());
            calendar.set(Calendar.YEAR, monthHeader.getYear());

            String monthYear = DateTimeUtils.formatMonthYear(calendar.getTime());
            tvEmptyMessage.setText(String.format("Không có sự kiện nào trong %s. Chạm để tạo mới!", monthYear));
        }
    }
    static class EventViewHolder extends RecyclerView.ViewHolder {
        private TextView tvDate;
        private TextView tvDayOfWeek;
        private TextView tvTitle;
        private TextView tvTime;
        private TextView tvLocation;
        private Context context;
        private EventAdapter.OnEventClickListener listener;

        public EventViewHolder(@NonNull View itemView, Context context, EventAdapter.OnEventClickListener listener) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tv_date);
            tvDayOfWeek = itemView.findViewById(R.id.tv_day_of_week);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvLocation = itemView.findViewById(R.id.tv_location);
            this.context = context;
            this.listener = listener;
        }

        public void bind(Event event) {
            if (event == null) return; // Tránh NullPointerException

            // Cập nhật click listener để sử dụng event hiện tại
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEventClick(event);
                }
            });

            tvDate.setText(DateTimeUtils.formatDate(event.getStartTime()));
            tvDayOfWeek.setText(DateTimeUtils.formatDayOfWeek(event.getStartTime()));
            tvTitle.setText(event.getTitle());

            if (event.isAllDay()) {
                tvTime.setText(R.string.all_day);
            } else {
                tvTime.setText(String.format("%s - %s",
                        DateTimeUtils.formatTime(event.getStartTime()),
                        DateTimeUtils.formatTime(event.getEndTime())));
            }

            if (event.getLocation() != null && !event.getLocation().isEmpty()) {
                tvLocation.setVisibility(View.VISIBLE);
                tvLocation.setText(event.getLocation());
            } else {
                tvLocation.setVisibility(View.GONE);
            }
        }
    }
    static class TitleViewHolder extends RecyclerView.ViewHolder {
        private TextView tvTitle;

        public TitleViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
        }

        public void bind(String title) {
            tvTitle.setText(title);
        }
    }
}