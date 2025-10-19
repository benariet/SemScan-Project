package com.yourpackage.yourapp.network;

import com.yourpackage.yourapp.Config;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiService {
    
    private static final String TAG = "ApiService";
    
    // Use your app's configured base URL instead of hardcoded values
    private static final String BASE_URL = Config.getServerUrl();
    
    private static Retrofit retrofit = new Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build();
    
    public static ApiInterface getApiInterface() {
        return retrofit.create(ApiInterface.class);
    }
    
    /**
     * Get the current base URL being used
     */
    public static String getBaseUrl() {
        return BASE_URL;
    }
    
    /**
     * Rebuild the Retrofit instance with a new base URL
     * This can be useful if you need to dynamically change the server
     */
    public static void rebuildRetrofit(String newBaseUrl) {
        retrofit = new Retrofit.Builder()
            .baseUrl(newBaseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build();
    }
}
