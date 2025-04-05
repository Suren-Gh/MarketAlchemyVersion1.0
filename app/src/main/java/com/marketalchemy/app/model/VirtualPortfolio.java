package com.marketalchemy.app.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.marketalchemy.app.api.BinanceTestnetClient;

import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Model class for handling virtual portfolio functionality
 */
public class VirtualPortfolio {
    
    private static final String TAG = "VirtualPortfolio";
    private static final String PREFS_NAME = "VirtualPortfolioPrefs";
    private static final String KEY_BALANCE = "virtualBalance";
    private static final String KEY_INVESTMENTS = "investments";
    
    private double balance;
    private List<Investment> investments;
    private final SharedPreferences prefs;
    private final Gson gson;
    private final BinanceTestnetClient binanceClient;
    
    public VirtualPortfolio(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        binanceClient = new BinanceTestnetClient();
        
        // Load saved balance
        balance = prefs.getFloat(KEY_BALANCE, 100000.0f); // Default $100,000
        
        // Load saved investments
        String investmentsJson = prefs.getString(KEY_INVESTMENTS, "");
        if (investmentsJson.isEmpty()) {
            investments = new ArrayList<>();
        } else {
            try {
                Type type = new TypeToken<List<Investment>>() {}.getType();
                investments = gson.fromJson(investmentsJson, type);
            } catch (Exception e) {
                Log.e(TAG, "Error loading investments: " + e.getMessage());
                investments = new ArrayList<>();
            }
        }
    }
    
    /**
     * Set the initial or add to virtual money balance
     * @param amount Amount to set or add
     * @param isAddition True if adding to balance, false if setting new balance
     */
    public void setBalance(double amount, boolean isAddition) {
        if (isAddition) {
            balance += amount;
        } else {
            balance = amount;
        }
        saveBalance();
    }
    
    /**
     * Get current virtual money balance
     * @return Current balance
     */
    public double getBalance() {
        return balance;
    }
    
    /**
     * Buy a cryptocurrency with virtual money using Binance testnet
     * @param cryptoId Cryptocurrency ID (e.g., "bitcoin")
     * @param quantity Amount to buy
     * @return True if purchase successful, false if insufficient funds
     */
    public boolean buyCrypto(String cryptoId, double quantity) {
        try {
            // Get current price from Binance testnet
            String symbol = getBinanceSymbol(cryptoId);
            double currentPrice = binanceClient.getCurrentPrice(symbol);
            
            double cost = quantity * currentPrice;
            
            // Check if user has enough funds
            if (cost > balance) {
                return false;
            }
            
            // Place order on Binance testnet
            JSONObject orderResponse = binanceClient.placeMarketBuyOrder(symbol, quantity);
            
            // Create or update investment
            boolean investmentExists = false;
            for (Investment investment : investments) {
                if (investment.getCryptoId().equals(cryptoId)) {
                    // Average out the purchase price based on quantity
                    double totalQuantity = investment.getQuantity() + quantity;
                    double totalValue = (investment.getQuantity() * investment.getPurchasePrice()) + (quantity * currentPrice);
                    double averagePrice = totalValue / totalQuantity;
                    
                    // Update the investment
                    investment.setQuantity(totalQuantity);
                    investment.setPurchasePrice(averagePrice);
                    investmentExists = true;
                    break;
                }
            }
            
            // If investment doesn't exist, create a new one
            if (!investmentExists) {
                investments.add(new Investment(cryptoId, quantity, currentPrice));
            }
            
            // Deduct from balance
            balance -= cost;
            
            // Save changes
            saveBalance();
            saveInvestments();
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error buying crypto: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Sell a cryptocurrency for virtual money using Binance testnet
     * @param cryptoId Cryptocurrency ID (e.g., "bitcoin")
     * @param quantity Amount to sell
     * @return True if sale successful, false if insufficient holdings
     */
    public boolean sellCrypto(String cryptoId, double quantity) {
        try {
            // Get current price from Binance testnet
            String symbol = getBinanceSymbol(cryptoId);
            double currentPrice = binanceClient.getCurrentPrice(symbol);
            
            for (int i = 0; i < investments.size(); i++) {
                Investment investment = investments.get(i);
                
                if (investment.getCryptoId().equals(cryptoId)) {
                    // Check if user has enough of the cryptocurrency
                    if (quantity > investment.getQuantity()) {
                        return false;
                    }
                    
                    // Place order on Binance testnet
                    JSONObject orderResponse = binanceClient.placeMarketSellOrder(symbol, quantity);
                    
                    // Calculate sale amount
                    double saleAmount = quantity * currentPrice;
                    
                    // Update investment or remove if fully sold
                    if (quantity == investment.getQuantity()) {
                        investments.remove(i);
                    } else {
                        investment.setQuantity(investment.getQuantity() - quantity);
                    }
                    
                    // Add sale amount to balance
                    balance += saleAmount;
                    
                    // Save changes
                    saveBalance();
                    saveInvestments();
                    
                    return true;
                }
            }
            
            return false; // Investment not found
        } catch (Exception e) {
            Log.e(TAG, "Error selling crypto: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get the current total portfolio value based on current prices from Binance testnet
     * @return Total portfolio value including cash balance
     */
    public double getTotalPortfolioValue() {
        try {
            double investmentValue = getInvestmentsValue();
            return balance + investmentValue;
        } catch (Exception e) {
            Log.e(TAG, "Error getting total portfolio value: " + e.getMessage());
            return balance;
        }
    }
    
    /**
     * Get the current value of all crypto investments from Binance testnet
     * @return Total value of all crypto investments
     */
    public double getInvestmentsValue() {
        try {
            double totalValue = 0;
            
            for (Investment investment : investments) {
                String cryptoId = investment.getCryptoId();
                String symbol = getBinanceSymbol(cryptoId);
                double currentPrice = binanceClient.getCurrentPrice(symbol);
                totalValue += investment.getQuantity() * currentPrice;
            }
            
            return totalValue;
        } catch (Exception e) {
            Log.e(TAG, "Error getting investments value: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Calculate total profit/loss based on current prices from Binance testnet
     * @return Total profit/loss value
     */
    public double getTotalProfitLoss() {
        try {
            double totalProfit = 0;
            
            for (Investment investment : investments) {
                String cryptoId = investment.getCryptoId();
                String symbol = getBinanceSymbol(cryptoId);
                double currentPrice = binanceClient.getCurrentPrice(symbol);
                double currentValue = investment.getQuantity() * currentPrice;
                double investedValue = investment.getQuantity() * investment.getPurchasePrice();
                totalProfit += (currentValue - investedValue);
            }
            
            return totalProfit;
        } catch (Exception e) {
            Log.e(TAG, "Error calculating profit/loss: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Get list of all investments
     * @return List of investments
     */
    public List<Investment> getInvestments() {
        return investments;
    }
    
    /**
     * Check if an investment exists for a specific cryptocurrency
     * @param cryptoId Cryptocurrency ID to check
     * @return True if investment exists
     */
    public boolean hasInvestment(String cryptoId) {
        for (Investment investment : investments) {
            if (investment.getCryptoId().equals(cryptoId)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get investment for a specific cryptocurrency
     * @param cryptoId Cryptocurrency ID to get
     * @return Investment or null if not found
     */
    public Investment getInvestment(String cryptoId) {
        for (Investment investment : investments) {
            if (investment.getCryptoId().equals(cryptoId)) {
                return investment;
            }
        }
        return null;
    }
    
    private void saveBalance() {
        prefs.edit().putFloat(KEY_BALANCE, (float) balance).apply();
    }
    
    private void saveInvestments() {
        String investmentsJson = gson.toJson(investments);
        prefs.edit().putString(KEY_INVESTMENTS, investmentsJson).apply();
    }
    
    /**
     * Convert cryptocurrency ID to Binance symbol
     * @param cryptoId Cryptocurrency ID (e.g., "bitcoin")
     * @return Binance symbol (e.g., "BTCUSDT")
     */
    private String getBinanceSymbol(String cryptoId) {
        switch (cryptoId.toLowerCase()) {
            case "bitcoin":
                return "BTCUSDT";
            case "ethereum":
                return "ETHUSDT";
            case "ripple":
                return "XRPUSDT";
            case "cardano":
                return "ADAUSDT";
            case "solana":
                return "SOLUSDT";
            case "polkadot":
                return "DOTUSDT";
            case "dogecoin":
                return "DOGEUSDT";
            case "avalanche-2":
                return "AVAXUSDT";
            case "chainlink":
                return "LINKUSDT";
            case "litecoin":
                return "LTCUSDT";
            default:
                throw new IllegalArgumentException("Unsupported cryptocurrency: " + cryptoId);
        }
    }
    
    /**
     * Inner class representing a single cryptocurrency investment
     */
    public static class Investment {
        private String cryptoId;
        private double quantity;
        private double purchasePrice;
        
        public Investment(String cryptoId, double quantity, double purchasePrice) {
            this.cryptoId = cryptoId;
            this.quantity = quantity;
            this.purchasePrice = purchasePrice;
        }
        
        public String getCryptoId() {
            return cryptoId;
        }
        
        public void setCryptoId(String cryptoId) {
            this.cryptoId = cryptoId;
        }
        
        public double getQuantity() {
            return quantity;
        }
        
        public void setQuantity(double quantity) {
            this.quantity = quantity;
        }
        
        public double getPurchasePrice() {
            return purchasePrice;
        }
        
        public void setPurchasePrice(double purchasePrice) {
            this.purchasePrice = purchasePrice;
        }
        
        /**
         * Calculate current value based on current price
         * @param currentPrice Current price of the cryptocurrency
         * @return Current value of the investment
         */
        public double getCurrentValue(double currentPrice) {
            return quantity * currentPrice;
        }
        
        /**
         * Calculate profit/loss based on current price
         * @param currentPrice Current price of the cryptocurrency
         * @return Profit/loss value (positive for profit, negative for loss)
         */
        public double getProfitLoss(double currentPrice) {
            double currentValue = getCurrentValue(currentPrice);
            double investedValue = quantity * purchasePrice;
            return currentValue - investedValue;
        }
        
        /**
         * Calculate profit/loss percentage based on current price
         * @param currentPrice Current price of the cryptocurrency
         * @return Profit/loss percentage (positive for profit, negative for loss)
         */
        public double getProfitLossPercentage(double currentPrice) {
            double profitLoss = getProfitLoss(currentPrice);
            double investedValue = quantity * purchasePrice;
            return (profitLoss / investedValue) * 100;
        }
    }
} 