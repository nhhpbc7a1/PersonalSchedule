package com.example.personalschedule.utils;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.style.ForegroundColorSpan;

import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.DayViewDecorator;
import com.prolificinteractive.materialcalendarview.DayViewFacade;
import com.prolificinteractive.materialcalendarview.spans.DotSpan;

import java.util.Collection;
import java.util.HashSet;

public class EventDecorator implements DayViewDecorator {

    private final Drawable highlightDrawable;
    private final HashSet<CalendarDay> dates;
    private final int color;

    public EventDecorator(int color, Collection<CalendarDay> dates) {
        highlightDrawable = new ColorDrawable(Color.parseColor("#228BC34A"));
        this.dates = new HashSet<>(dates);
        this.color = color;
    }

    @Override
    public boolean shouldDecorate(CalendarDay day) {
        return dates.contains(day);
    }

    @Override
    public void decorate(com.prolificinteractive.materialcalendarview.DayViewFacade view) {
        view.addSpan(new DotSpan(5, color)); // Đặt màu và kích thước của dấu chấm
    }
}