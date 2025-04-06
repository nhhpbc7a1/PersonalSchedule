package com.example.personalschedule.activities;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.personalschedule.R;
import com.example.personalschedule.adapters.EventAdapter;
import com.example.personalschedule.adapters.MonthHeaderAdapter;
import com.example.personalschedule.database.EventRepository;
import com.example.personalschedule.models.Event;
import com.example.personalschedule.utils.Constants;
import com.example.personalschedule.utils.EventDecorator;
import com.example.personalschedule.utils.NotificationUtils;
import com.example.personalschedule.viewmodels.EventListViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.MaterialCalendarView;
import com.prolificinteractive.materialcalendarview.OnDateSelectedListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements EventAdapter.OnEventClickListener, OnDateSelectedListener {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_ACCOUNT_PICKER = 1000;
    private static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1002;
    private static final String PREF_ACCOUNT_NAME = "accountName";
    
    private MaterialCalendarView calendarView;
    private RecyclerView recyclerView;
    private MonthHeaderAdapter adapter;
    private EventListViewModel viewModel;
    private FloatingActionButton fabAddEvent;
    private TextView tvNoEvents;
    private Calendar selectedDate = Calendar.getInstance();
    private EventRepository eventRepository;
    private ActivityResultLauncher<Intent> addEditEventLauncher;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Create notification channel for event reminders
        NotificationUtils.createNotificationChannel(this);
        
        // Khởi tạo cơ bản
        preferences = getPreferences(MODE_PRIVATE);
        initViews();
        setupCalendar();
        setupRecyclerView();
        setupFab();
        setupActivityLauncher();
        
        // Initialize repository
        eventRepository = new EventRepository(getApplication());
        
        // Load events for today
        loadEventsForDate(CalendarDay.today());
    }
    
    private boolean isGoogleAccountSelected() {
        String accountName = preferences.getString(PREF_ACCOUNT_NAME, null);
        return accountName != null;
    }
    
    private void promptForGoogleAccount() {
        // Đơn giản hóa quy trình lựa chọn tài khoản Google
        AccountManager accountManager = AccountManager.get(this);
        Account[] accounts = accountManager.getAccountsByType("com.google");
        
        if (accounts.length == 0) {
            Toast.makeText(this, getString(R.string.account_required), Toast.LENGTH_LONG).show();
            return;
        }
        
        // Sử dụng tài khoản đầu tiên một cách đơn giản
        saveSelectedAccount(accounts[0].name);
    }
    
    private void saveSelectedAccount(String accountName) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(PREF_ACCOUNT_NAME, accountName);
        editor.apply();
    }

    private void initViews() {
        calendarView = findViewById(R.id.calendar_view);
        recyclerView = findViewById(R.id.recycler_view_events);
        fabAddEvent = findViewById(R.id.fab_add_event);
        tvNoEvents = findViewById(R.id.tv_no_events);

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        EventAdapter eventAdapter = new EventAdapter(this, this);
        adapter = new MonthHeaderAdapter(this, this);
        recyclerView.setAdapter(adapter);

        // Setup ViewModel
        viewModel = new ViewModelProvider(this).get(EventListViewModel.class);
    }

    private void setupCalendar() {
        // Lắng nghe sự kiện khi ngày được chọn
        calendarView.setOnDateChangedListener(this);
    }

    private void setupRecyclerView() {
        // Load sự kiện từ ViewModel và đánh dấu ngày trên lịch
        viewModel.getEventList().observe(this, events -> {
            // Tạo một tập hợp CalendarDay cho những ngày có sự kiện
            HashSet<CalendarDay> eventDays = new HashSet<>();
            for (Event event : events) {
                Calendar calendar = Calendar.getInstance();
                // Sử dụng setTimeInMillis thay vì setTime vì getStartTime trả về long
                calendar.setTimeInMillis(event.getStartTime());
                // Sử dụng phương thức from chính xác nhất
                CalendarDay day = CalendarDay.from(
                    calendar.get(Calendar.YEAR), 
                    calendar.get(Calendar.MONTH) + 1, // Tháng trong CalendarDay bắt đầu từ 1
                    calendar.get(Calendar.DAY_OF_MONTH)
                );
                eventDays.add(day);
            }

            // Tạo EventDecorator
            EventDecorator eventDecorator = new EventDecorator(Color.RED, eventDays); // Màu đỏ để đánh dấu

            // Xóa tất cả các decorator hiện có (nếu có) và thêm decorator mới
            calendarView.removeDecorators();
            calendarView.addDecorator(eventDecorator);
            List<Object> objectEvents = new ArrayList<>(events);
            adapter.setEvents(objectEvents);
        });
    }

    private void setupFab() {
        // Setup FAB
        fabAddEvent.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AddEditEventActivity.class);
            startActivityForResult(intent, Constants.REQUEST_ADD_EVENT);
        });
    }

    private void setupActivityLauncher() {
        addEditEventLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null) {
                        // We need to check the action or extras from the intent
                        // since getRequestCode() isn't available in ActivityResult
                        updateEventList();
                        viewModel.refreshCurrentList();
                    }
                }
            }
        );
    }

    private void updateEventList() {
        SimpleDateFormat monthYearFormat = new SimpleDateFormat("MM-yyyy", Locale.getDefault());
        String monthYear = monthYearFormat.format(selectedDate.getTime());

        viewModel.getEventsByMonth(monthYear).observe(this, events -> {
            List<Object> sortedEvents = new ArrayList<>(); // Thay Event bằng Object
            List<Event> selectedDayEvents = new ArrayList<>();
            List<Event> otherEvents = new ArrayList<>();

            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            String selectedDateString = dateFormat.format(selectedDate.getTime());

            // Phân loại sự kiện
            for (Event event : events) {
                // Tạo Date từ timestamp để sử dụng với SimpleDateFormat
                Date eventDate = new Date(event.getStartTime());
                String eventDateString = dateFormat.format(eventDate);
                if (eventDateString.equals(selectedDateString)) {
                    selectedDayEvents.add(event);
                } else {
                    otherEvents.add(event);
                }
            }

            // Thêm sự kiện của ngày được chọn nếu có
            if (!selectedDayEvents.isEmpty()) {
                sortedEvents.add("Sự kiện trong ngày"); // Thêm tiêu đề dạng String
                sortedEvents.addAll(selectedDayEvents);
            }

            // Thêm tiêu đề và các sự kiện còn lại trong tháng
            if (!otherEvents.isEmpty()) {
                sortedEvents.add("Các sự kiện khác trong tháng"); // Thêm tiêu đề dạng String
                sortedEvents.addAll(otherEvents);
            }

            // Cập nhật adapter với danh sách có tiêu đề
            adapter.setEvents(sortedEvents);
            
            // Hiển thị hoặc ẩn thông báo không có sự kiện
            if (events.isEmpty()) {
                tvNoEvents.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                tvNoEvents.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                viewModel.setSearchQuery(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                viewModel.setSearchQuery(newText);
                return true;
            }
        });

        // Reset search when closed
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                viewModel.setSearchQuery("");
                return true;
            }
        });

        return true;
    }

    @Override
    public void onEventClick(Event event) {
        Intent intent = new Intent(this, EventDetailActivity.class);
        intent.putExtra(Constants.EXTRA_EVENT_ID, event.getId());
        startActivityForResult(intent, Constants.REQUEST_EDIT_EVENT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh data mỗi khi activity trở lại foreground
        updateEventList();
        // Cũng refresh danh sách event toàn bộ để cập nhật calendar dots
        viewModel.refreshCurrentList();
    }

    @Override
    public void onDateSelected(@NonNull MaterialCalendarView widget, @NonNull CalendarDay date, boolean selected) {
        // Convert CalendarDay to Calendar
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, date.getYear());
        calendar.set(Calendar.MONTH, date.getMonth() - 1); // CalendarDay months start at 1, Calendar months start at 0
        calendar.set(Calendar.DAY_OF_MONTH, date.getDay());
        selectedDate = calendar;
        
        // Update the event list based on the selected date
        updateEventList();
    }

    /**
     * Loads events for the specified date
     * @param date The CalendarDay to load events for
     */
    private void loadEventsForDate(CalendarDay date) {
        // Convert CalendarDay to Calendar
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, date.getYear());
        calendar.set(Calendar.MONTH, date.getMonth() - 1); // CalendarDay months start at 1, Calendar months start at 0
        calendar.set(Calendar.DAY_OF_MONTH, date.getDay());
        selectedDate = calendar;
        
        // Update the event list
        updateEventList();
        
        // Also select the date in the calendar view
        calendarView.setSelectedDate(date);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Refresh data after add, edit, or delete
        if ((requestCode == Constants.REQUEST_ADD_EVENT && resultCode == Constants.RESULT_EVENT_ADDED) ||
                (requestCode == Constants.REQUEST_EDIT_EVENT &&
                        (resultCode == Constants.RESULT_EVENT_UPDATED || resultCode == Constants.RESULT_EVENT_DELETED))) {
            // Data will be refreshed automatically via LiveData
            updateEventList();
            viewModel.refreshCurrentList(); // Refresh entire list
        }
    }
}