package com.marketalchemy.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.app.AlertDialog;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import java.util.Currency;
import android.animation.ValueAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import com.marketalchemy.app.api.BinanceWebSocketClient;
import android.util.TypedValue;
import com.marketalchemy.app.model.VirtualPortfolio;

public class CryptoFragment extends Fragment {

    private TextView tvPrice, tvChange, tvVirtualBalance, tvPortfolioValue;
    private ProgressBar progressBar;
    private Button buyButton, sellButton;
    private Button addMoneyButton;
    private Button setMoneyButton;
    private EditText quantityInput;
    
    private final OkHttpClient client = new OkHttpClient();
    private final ExecutorService executorService = Executors.newFixedThreadPool(1);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);

    // Virtual portfolio for consistent portfolio tracking
    private VirtualPortfolio portfolio;
    
    // Shared preferences for transaction history
    private SharedPreferences prefs;
    private static final String PREFS_NAME = "MarketAlchemyPrefs";
    private static final String KEY_TRANSACTION_HISTORY = "transaction_history";
    
    private BinanceWebSocketClient webSocketClient;
    
    // Handler for periodic updates
    private Handler updateHandler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_crypto, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize SharedPreferences and portfolio
        prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        portfolio = new VirtualPortfolio(requireContext());
        
        // Initialize the currency format explicitly to ensure $ symbol
        currencyFormat.setCurrency(Currency.getInstance("USD"));
        
        // Initialize UI components
        progressBar = view.findViewById(R.id.progressBar);
        tvPrice = view.findViewById(R.id.tvBitcoinPrice);
        tvChange = view.findViewById(R.id.tvBitcoinChange);
        
        // Initialize portfolio views
        tvVirtualBalance = view.findViewById(R.id.tvVirtualBalance);
        tvPortfolioValue = view.findViewById(R.id.tvPortfolioValue);
        
        // Initialize trading controls
        quantityInput = view.findViewById(R.id.quantityInput);
        buyButton = view.findViewById(R.id.buyButton);
        sellButton = view.findViewById(R.id.sellButton);
        addMoneyButton = view.findViewById(R.id.addMoneyButton);
        setMoneyButton = view.findViewById(R.id.setMoneyButton);
        
        // Set up button click listeners
        buyButton.setOnClickListener(v -> handleBuy());
        sellButton.setOnClickListener(v -> handleSell());
        addMoneyButton.setOnClickListener(v -> handleAddMoney());
        setMoneyButton.setOnClickListener(v -> handleSetMoney());
        
        // Make sure loading indicator is visible
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(Color.parseColor("#FFD700")));
        progressBar.setVisibility(View.VISIBLE);
        
        // Initialize WebSocket client
        webSocketClient = new BinanceWebSocketClient();
        
        // Set up price update listener
        webSocketClient.setPriceUpdateListener((symbol, price, change) -> {
            mainHandler.post(() -> {
                if (symbol.equals("BTCUSDT")) {
                    updatePriceDisplay(price, change);
                    updatePortfolioDisplay();
                }
            });
        });
        
        // Connect to WebSocket for Bitcoin
        webSocketClient.connect("BTCUSDT");
        
        // Update UI title
        TextView titleView = view.findViewById(R.id.tvBitcoinTitle);
        titleView.setText("Bitcoin");
        
        // Set up periodic updates
        setupPeriodicUpdates();
        
        // Initialize portfolio display
        updatePortfolioDisplay();
        
        Log.d("CryptoFragment", "Connected to Binance testnet WebSocket for Bitcoin");
        Toast.makeText(requireContext(), "Connected to Binance testnet", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Update portfolio display when returning to this fragment
        updatePortfolioDisplay();
    }
    
    /**
     * Updates the portfolio display with the current values from VirtualPortfolio
     */
    private void updatePortfolioDisplay() {
        // Get current values from portfolio
        double balance = portfolio.getBalance();
        double totalValue = portfolio.getTotalPortfolioValue();
        double investmentsValue = portfolio.getInvestmentsValue();
        
        // Update UI
        tvVirtualBalance.setText(currencyFormat.format(balance));
        tvPortfolioValue.setText(currencyFormat.format(totalValue));
    }
    
    private void setupPeriodicUpdates() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                fetchCryptoData();
                updatePortfolioDisplay(); // Update portfolio values
                updateHandler.postDelayed(this, 1000); // Update every second
            }
        };
        updateHandler.post(updateRunnable);
    }
    
    private void handleBuy() {
        try {
            String quantityStr = quantityInput.getText().toString();
            if (!quantityStr.isEmpty()) {
                double quantity = Double.parseDouble(quantityStr);
                if (quantity > 0) {
                    // Get current price from tvPrice
                    String priceStr = tvPrice.getText().toString().replaceAll("[^0-9.]", "");
                    double currentPrice = Double.parseDouble(priceStr);
                    double totalCost = quantity * currentPrice;
                    
                    // Try to buy using the portfolio
                    boolean success = portfolio.buyCrypto("bitcoin", quantity);
                    
                    if (success) {
                        // Save transaction to history
                        String transaction = String.format(Locale.US, 
                            "BUY %.8f BTC @ $%.2f = $%.2f", 
                            quantity, currentPrice, totalCost);
                        saveTransaction(transaction);
                        
                        // Update UI
                        updatePortfolioDisplay();
                        
                        // Show success message with transaction details
                        String successMessage = String.format(Locale.US,
                            "Successfully bought %.8f BTC\nPrice: $%.2f\nTotal: $%.2f",
                            quantity, currentPrice, totalCost);
                        Toast.makeText(requireContext(), successMessage, Toast.LENGTH_LONG).show();
                        
                        // Clear input field
                        quantityInput.setText("");
                    } else {
                        Toast.makeText(requireContext(), 
                            "Insufficient funds. You need $" + String.format(Locale.US, "%.2f", totalCost),
                            Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(requireContext(), 
                        "Please enter a positive quantity",
                        Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(requireContext(), 
                    "Please enter a quantity",
                    Toast.LENGTH_SHORT).show();
            }
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), 
                "Invalid quantity",
                Toast.LENGTH_SHORT).show();
        }
    }
    
    private void saveTransaction(String transaction) {
        String history = prefs.getString(KEY_TRANSACTION_HISTORY, "");
        history = transaction + "\n" + history;
        prefs.edit().putString(KEY_TRANSACTION_HISTORY, history).apply();
    }
    
    private void handleSell() {
        try {
            String quantityStr = quantityInput.getText().toString();
            if (!quantityStr.isEmpty()) {
                double quantity = Double.parseDouble(quantityStr);
                if (quantity > 0) {
                    // Get current price from tvPrice
                    String priceStr = tvPrice.getText().toString().replaceAll("[^0-9.]", "");
                    double currentPrice = Double.parseDouble(priceStr);
                    double totalValue = quantity * currentPrice;
                    
                    // Check if the user has Bitcoin investment
                    if (portfolio.hasInvestment("bitcoin")) {
                        // Try to sell using the portfolio
                        boolean success = portfolio.sellCrypto("bitcoin", quantity);
                        
                        if (success) {
                            // Save transaction to history
                            String transaction = String.format(Locale.US, 
                                "SELL %.8f BTC @ $%.2f = $%.2f", 
                                quantity, currentPrice, totalValue);
                            saveTransaction(transaction);
                            
                            // Update UI
                            updatePortfolioDisplay();
                            
                            // Show success message
                            String successMessage = String.format(Locale.US,
                                "Successfully sold %.8f BTC\nPrice: $%.2f\nTotal: $%.2f",
                                quantity, currentPrice, totalValue);
                            Toast.makeText(requireContext(), successMessage, Toast.LENGTH_LONG).show();
                            
                            // Clear input field
                            quantityInput.setText("");
                        } else {
                            Toast.makeText(requireContext(), 
                                "Insufficient Bitcoin holdings",
                                Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(requireContext(), 
                            "You don't own any Bitcoin to sell",
                            Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(requireContext(), 
                        "Please enter a positive quantity",
                        Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(requireContext(), 
                    "Please enter a quantity",
                    Toast.LENGTH_SHORT).show();
            }
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), 
                "Invalid quantity",
                Toast.LENGTH_SHORT).show();
        }
    }
    
    private void handleAddMoney() {
        // Create an EditText for the user to input the amount
        final EditText input = new EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("Enter amount to add");
        
        // Create padding for the input
        int padding = (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 16, 
            getResources().getDisplayMetrics()
        );
        
        // Set layout parameters for the EditText
        FrameLayout container = new FrameLayout(requireContext());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = padding;
        params.rightMargin = padding;
        input.setLayoutParams(params);
        container.addView(input);

        // Build the dialog
        new AlertDialog.Builder(requireContext())
            .setTitle("Add Money to Portfolio")
            .setMessage("Enter the amount you want to add to your portfolio")
            .setView(container)
            .setPositiveButton("Add", (dialog, which) -> {
                String amountStr = input.getText().toString();
                if (!amountStr.isEmpty()) {
                    try {
                        double amount = Double.parseDouble(amountStr);
                        if (amount > 0) {
                            // Add the money to the portfolio (true means it's an addition)
                            portfolio.setBalance(amount, true);
                            
                            // Update the UI
                            updatePortfolioDisplay();
                            
                            // Show success message
                            Toast.makeText(requireContext(), 
                                "Successfully added $" + String.format(Locale.US, "%.2f", amount) + " to your portfolio", 
                                Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(), 
                                "Please enter a positive amount", 
                                Toast.LENGTH_SHORT).show();
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(), 
                            "Invalid amount", 
                            Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(requireContext(), 
                        "Please enter an amount", 
                        Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void handleSetMoney() {
        // Create an EditText for the user to input the amount
        final EditText input = new EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("Enter amount to set");
        
        // Create padding for the input
        int padding = (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 16, 
            getResources().getDisplayMetrics()
        );
        
        // Set layout parameters for the EditText
        FrameLayout container = new FrameLayout(requireContext());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = padding;
        params.rightMargin = padding;
        input.setLayoutParams(params);
        container.addView(input);

        // Build the dialog
        new AlertDialog.Builder(requireContext())
            .setTitle("Set Money in Portfolio")
            .setMessage("Enter the amount you want to set in your portfolio")
            .setView(container)
            .setPositiveButton("Set", (dialog, which) -> {
                String amountStr = input.getText().toString();
                if (!amountStr.isEmpty()) {
                    try {
                        double amount = Double.parseDouble(amountStr);
                        if (amount > 0) {
                            // Set the money in the portfolio (false means it's setting a new value, not adding)
                            portfolio.setBalance(amount, false);
                            
                            // Update the UI
                            updatePortfolioDisplay();
                            
                            // Show success message
                            Toast.makeText(requireContext(), 
                                "Successfully set $" + String.format(Locale.US, "%.2f", amount) + " in your portfolio", 
                                Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(), 
                                "Please enter a positive amount", 
                                Toast.LENGTH_SHORT).show();
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(), 
                            "Invalid amount", 
                            Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(requireContext(), 
                        "Please enter an amount", 
                        Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void fetchCryptoData() {
        executorService.execute(() -> {
            try {
                // API endpoint for price data
                String url = "https://testnet.binance.vision/api/v3/ticker/price?symbol=BTCUSDT";
            
            Request request = new Request.Builder()
                    .url(url)
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected code " + response);
                }
                
                String responseData = response.body().string();
                JSONObject json = new JSONObject(responseData);
                    double price = Double.parseDouble(json.getString("price"));
                
                mainHandler.post(() -> {
                        updatePriceDisplay(price, 0.0); // Change percentage will be updated by WebSocket
                        showLoading(false);
                });
                
            } catch (IOException | JSONException e) {
                    Log.e("CryptoFragment", "Error fetching data: " + e.getMessage());
                    mainHandler.post(() -> showLoading(false));
            }
        } catch (Exception e) {
                Log.e("CryptoFragment", "Error in fetchCryptoData: " + e.getMessage());
                mainHandler.post(() -> showLoading(false));
            }
        });
    }
    
    private void updatePriceDisplay(double price, double change) {
        // Format price with 2 decimal places
        String formattedPrice = String.format(Locale.US, "%.2f", price);
        
        // Update price with animation
        animateTextChange(tvPrice, "$" + formattedPrice);
        
        // Update change percentage with 2 decimal places
        String changeText = String.format(Locale.US, "%.2f%%", change);
        if (change >= 0) {
            tvChange.setText("+" + changeText);
            // Get color from theme attribute
            TypedValue typedValue = new TypedValue();
            requireContext().getTheme().resolveAttribute(R.attr.chartIncreasing, typedValue, true);
            tvChange.setTextColor(typedValue.data);
        } else {
            tvChange.setText(changeText);
            // Get color from theme attribute
            TypedValue typedValue = new TypedValue();
            requireContext().getTheme().resolveAttribute(R.attr.chartDecreasing, typedValue, true);
            tvChange.setTextColor(typedValue.data);
        }
    }
    
    private void animateTextChange(final TextView textView, final String newText) {
        ValueAnimator fadeOut = ValueAnimator.ofFloat(1.0f, 0.5f);
            fadeOut.setDuration(150);
        fadeOut.addUpdateListener(animator -> textView.setAlpha((float) animator.getAnimatedValue()));
        fadeOut.addListener(new AnimatorListenerAdapter() {
                @Override
            public void onAnimationEnd(Animator animation) {
                    textView.setText(newText);
                ValueAnimator fadeIn = ValueAnimator.ofFloat(0.5f, 1.0f);
                    fadeIn.setDuration(150);
                fadeIn.addUpdateListener(animator -> textView.setAlpha((float) animator.getAnimatedValue()));
                fadeIn.start();
            }
        });
        fadeOut.start();
    }
    
    private void showLoading(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }
    
    @Override
    public void onDestroy() {
        executorService.shutdown();
        if (webSocketClient != null) {
            webSocketClient.disconnect();
        }
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
        super.onDestroy();
    }
} 