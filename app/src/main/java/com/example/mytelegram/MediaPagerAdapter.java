package com.example.mytelegram;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.mytelegram.Fragments.DocumentFragment;
import com.example.mytelegram.Fragments.GalleryFragment;

public class MediaPagerAdapter extends FragmentStateAdapter {

    public MediaPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) {
            return new GalleryFragment();
        } else {
            return new DocumentFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}