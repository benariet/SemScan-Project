package com.yourpackage.yourapp.network;

import com.yourpackage.yourapp.models.Session;
import com.yourpackage.yourapp.models.Seminar;
import com.yourpackage.yourapp.models.Attendance;
import com.yourpackage.yourapp.models.ManualAttendanceRequest;
import com.yourpackage.yourapp.models.ManualAttendanceResponse;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiInterface {

    // Existing endpoints
    @GET("api/v1/seminars")
    Call<List<Seminar>> getSeminars(@Header("x-api-key") String apiKey);

    @GET("api/v1/sessions")
    Call<List<Session>> getSessions(@Header("x-api-key") String apiKey);

    @GET("api/v1/sessions/open")
    Call<List<Session>> getOpenSessions(@Header("x-api-key") String apiKey);

    @GET("api/v1/sessions/{sessionId}")
    Call<Session> getSession(@Path("sessionId") String sessionId, @Header("x-api-key") String apiKey);

    @GET("api/v1/attendance")
    Call<List<Attendance>> getAttendanceBySession(@Query("sessionId") String sessionId, @Header("x-api-key") String apiKey);

    // Manual Attendance endpoints
    @POST("api/v1/attendance/manual-request")
    Call<ManualAttendanceResponse> createManualRequest(@Body ManualAttendanceRequest request);

    @GET("api/v1/attendance/pending-requests")
    Call<List<ManualAttendanceResponse>> getPendingRequests(
        @Query("sessionId") String sessionId,
        @Header("x-api-key") String apiKey
    );

    @POST("api/v1/attendance/{id}/approve")
    Call<ManualAttendanceResponse> approveRequest(
        @Path("id") String attendanceId,
        @Header("x-api-key") String apiKey
    );

    @POST("api/v1/attendance/{id}/reject")
    Call<ManualAttendanceResponse> rejectRequest(
        @Path("id") String attendanceId,
        @Header("x-api-key") String apiKey
    );

    // Export endpoints
    @GET("api/v1/export/csv")
    Call<byte[]> exportCsv(@Query("sessionId") String sessionId, @Header("x-api-key") String apiKey);

    @GET("api/v1/export/xlsx")
    Call<byte[]> exportXlsx(@Query("sessionId") String sessionId, @Header("x-api-key") String apiKey);

    // Dynamic configuration endpoints (no API key required)
    @GET("api/v1/info/endpoints")
    Call<Object> getApiEndpoints();

    @GET("api/v1/info/config")
    Call<Object> getApiConfig();
}
