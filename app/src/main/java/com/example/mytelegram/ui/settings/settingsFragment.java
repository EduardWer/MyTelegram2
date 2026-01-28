package com.example.mytelegram.ui.settings;

import androidx.lifecycle.ViewModelProvider;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.mytelegram.LoginActivity;
import com.example.mytelegram.R;
import com.example.mytelegram.databinding.FragmentHomeBinding;
import com.example.mytelegram.databinding.FragmentSettingsBinding;
import com.example.mytelegram.ui.home.HomeViewModel;
import com.google.firebase.auth.FirebaseAuth;

public class settingsFragment extends Fragment {

    private FirebaseAuth mAuth;
    private FragmentSettingsBinding binding; // ViewBinding

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 2. Инициализируем Firebase Auth
        mAuth = FirebaseAuth.getInstance();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnLogout.setOnClickListener(v -> {
            // 3. Теперь mAuth доступна
            mAuth.signOut();
            Toast.makeText(getContext(), "Вы вышли из системы", Toast.LENGTH_SHORT).show();
            navigateToAuthScreen();
        });
    }

    private void navigateToAuthScreen() {
        requireActivity().finish();
        startActivity(new Intent(getActivity(), LoginActivity.class));
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}