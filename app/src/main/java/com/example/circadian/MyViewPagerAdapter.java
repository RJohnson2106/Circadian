package com.example.circadian;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.circadian.fragments.ScheduleFragment;
import com.example.circadian.fragments.TipsFragment;
import com.example.circadian.fragments.TrackerFragment;

public class MyViewPagerAdapter extends FragmentStateAdapter {
    public MyViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position){
            case 0:
                return new ScheduleFragment();
            case 1:
                return new TrackerFragment();
            case 2:
                return new TipsFragment();
            default:
                return new ScheduleFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
