package com.example.circadian.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.Build;
import android.widget.Toast;


import com.example.circadian.AlarmReceiver;
import com.google.android.material.timepicker.MaterialTimePicker;

import java.util.Calendar;

import com.example.circadian.R;
import com.example.circadian.databinding.FragmentScheduleBinding;
import com.google.android.material.timepicker.TimeFormat;


public class ScheduleFragment extends Fragment {

    private @NonNull FragmentScheduleBinding binding;
    private MaterialTimePicker timePicker;
    private Calendar calendar;
    private AlarmManager alarmManager;
    private PendingIntent pendingIntent;


    // TODO: Fix text of 12:00 AM being displayed as 00:00 AM instead of 12:00 AM; FIX AM PM error
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_schedule, container, false);
        binding = FragmentScheduleBinding.bind(rootView);

        createNotificationChannel();

        binding.selectTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                timePicker = new MaterialTimePicker.Builder()
                        .setTimeFormat(TimeFormat.CLOCK_12H)
                        .setHour(12)
                        .setMinute(0)
                        .setTitleText("Select Alarm Time")
                        .build();

                timePicker.show(requireActivity().getSupportFragmentManager(), "");
                timePicker.addOnPositiveButtonClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        int hour = timePicker.getHour();
                        int minute = timePicker.getMinute();

                        int hour_disp = hour;

                        String timeFormat;

                        if (hour_disp >= 12) {
                            if (hour_disp > 12) {
                                hour_disp -= 12;
                            }
                            timeFormat = "PM";
                        } else {
                            if (hour_disp == 0) {
                                hour_disp = 12;
                            }
                            timeFormat = "AM";
                        }

                        String formattedHour = String.format("%02d", hour_disp);
                        String formattedMinute = String.format("%02d", minute);

                        binding.selectTime.setText(formattedHour + ":" + formattedMinute + " " + timeFormat);

                        calendar = Calendar.getInstance();
                        calendar.set(Calendar.HOUR_OF_DAY, hour);
                        calendar.set(Calendar.MINUTE, minute);
                        calendar.set(Calendar.SECOND, 0);
                        calendar.set(Calendar.MILLISECOND, 0);
                    }
                });
            }
        });

        binding.setAlarm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (calendar == null) {
                    Toast.makeText(requireContext(), "You need to set a time by tapping the clock", Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent intent = new Intent(requireContext(), AlarmReceiver.class);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(requireContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

                AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);

                // Cancel any existing alarms
                alarmManager.cancel(pendingIntent);

                // Set the alarm to go off at the specified time
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);

                // Notify the user that the alarm is set
                Toast.makeText(requireContext(), "Alarm Set for " + binding.selectTime.getText(), Toast.LENGTH_SHORT).show();
            }
        });

        binding.cancelAlarm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(requireContext(), AlarmReceiver.class);
                pendingIntent = PendingIntent.getBroadcast(requireContext(), 0, intent, PendingIntent.FLAG_IMMUTABLE);

                if (alarmManager == null){
                    alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
                }

                alarmManager.cancel(pendingIntent);
                Toast.makeText(requireContext(), "Alarm Canceled", Toast.LENGTH_SHORT).show();
            }
        });

        return rootView; // Return the inflated rootView
    }

    private void createNotificationChannel(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Circadian Channel";
            String description = "Channel for Circadian notifications";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel("circadian_channel", name, importance);
            channel.setDescription(description);
            NotificationManager  notificationManager = requireActivity().getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}