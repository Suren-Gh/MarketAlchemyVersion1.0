package com.marketalchemy.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.marketalchemy.app.model.VirtualPortfolio;

import java.text.NumberFormat;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private TextView tvUsername;
    private TextView tvPortfolioValue;
    private TextView tvProfitLoss;
    private ImageView ivNotifications;
    private FirebaseAuth mAuth;
    private VirtualPortfolio portfolio;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        
        // Initialize portfolio
        portfolio = new VirtualPortfolio(requireContext());
        
        // Initialize views
        tvUsername = view.findViewById(R.id.tvUsername);
        tvPortfolioValue = view.findViewById(R.id.tvPortfolioValue);
        tvProfitLoss = view.findViewById(R.id.tvProfitLoss);
        ivNotifications = view.findViewById(R.id.ivNotifications);
        
        // Set click listener for profile button
        ivNotifications.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Navigate to profile fragment
                if (getActivity() != null) {
                    ((MainActivity) getActivity()).navigateToProfile();
                }
            }
        });
        
        // Update UI with user and portfolio data
        updateUserInfo();
        updatePortfolioInfo();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Update portfolio data when returning to this fragment
        updatePortfolioInfo();
    }
    
    private void updateUserInfo() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String displayName = currentUser.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                // Extract username if available
                if (displayName.contains("(") && displayName.contains(")")) {
                    String username = displayName.substring(
                            displayName.indexOf("(") + 1, 
                            displayName.indexOf(")")
                    );
                    tvUsername.setText("@" + username);
                } else {
                    tvUsername.setText(displayName);
                }
            }
        }
    }
    
    private void updatePortfolioInfo() {
        // Get portfolio total value
        double totalValue = portfolio.getTotalPortfolioValue();
        
        // Format with currency symbol and commas
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);
        tvPortfolioValue.setText(currencyFormat.format(totalValue));
        
        // Update profit/loss information if available
        if (tvProfitLoss != null) {
            double profitLoss = portfolio.getTotalProfitLoss();
            double profitLossPercent = (profitLoss / (totalValue - profitLoss)) * 100;
            
            String profitLossText = String.format("%s (%.1f%%)", 
                    currencyFormat.format(profitLoss), 
                    profitLossPercent);
            
            tvProfitLoss.setText(profitLossText);
            
            // Set color based on profit/loss
            if (profitLoss >= 0) {
                tvProfitLoss.setTextColor(getResources().getColor(android.R.color.holo_green_light));
            } else {
                tvProfitLoss.setTextColor(getResources().getColor(android.R.color.holo_red_light));
            }
        }
    }
} 