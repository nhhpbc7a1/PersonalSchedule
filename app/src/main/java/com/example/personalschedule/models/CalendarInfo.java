package com.example.personalschedule.models;

public class CalendarInfo {
    private long id;
    private String displayName;
    private String accountName;
    private boolean isPrimary; // Hoặc thêm các trường cần thiết khác

    public CalendarInfo(long id, String displayName, String accountName, boolean isPrimary) {
        this.id = id;
        this.displayName = displayName;
        this.accountName = accountName;
        this.isPrimary = isPrimary;
    }

    public long getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getAccountName() { return accountName; }
    public boolean isPrimary() { return isPrimary; }

    // Quan trọng: Override toString() để hiển thị tên trong Spinner
    @Override
    public String toString() {
        return displayName; // Hoặc displayName + " (" + accountName + ")"
    }
}