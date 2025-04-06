package com.example.personalschedule.utils;

public class Constants {
    // Intent extras
    public static final String EXTRA_EVENT_ID = "extra_event_id";
    public static final String EXTRA_EVENT_TITLE = "extra_event_title";

    // Request codes
    public static final int REQUEST_ADD_EVENT = 1001;
    public static final int REQUEST_EDIT_EVENT = 1002;

    // Result codes
    public static final int RESULT_EVENT_ADDED = 2001;
    public static final int RESULT_EVENT_UPDATED = 2002;
    public static final int RESULT_EVENT_DELETED = 2003;

    // Priority levels
    public static final int PRIORITY_LOW = 0;
    public static final int PRIORITY_MEDIUM = 1;
    public static final int PRIORITY_HIGH = 2;

    // Reminder options (in minutes)
    public static final int[] REMINDER_OPTIONS = {0, 5, 10, 15, 30, 60, 120, 1440};

    // Recurrence patterns
    public static final String RECURRENCE_NONE = "NONE";
    public static final String RECURRENCE_DAILY = "DAILY";
    public static final String RECURRENCE_WEEKLY = "WEEKLY";
    public static final String RECURRENCE_MONTHLY = "MONTHLY";
    public static final String RECURRENCE_YEARLY = "YEARLY";
}