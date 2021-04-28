package com.taticus.godot.plugin.android.firebase;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.PlayGamesAuthProvider;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.perf.metrics.HttpMetric;
import com.google.firebase.perf.metrics.Trace;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Timer;

import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FirebasePlugin extends GodotPlugin {

    private static final String TAG = "FirebasePlugin";
    private static final int RC_PLAY_GAMES_SIGN_IN = 9001;
    private static final int RC_GOOGLE_SIGN_IN = 9002;

    private final SignalInfo loginSuccessfullySignal = new SignalInfo("login_successfully");
    private final SignalInfo loginFailedSignal = new SignalInfo("login_failed", String.class);
    private final SignalInfo idTokenLoadedSignal = new SignalInfo("id_token_loaded", String.class);
    private final SignalInfo idTokenFailedSignal = new SignalInfo("id_token_failed", String.class);
    private final SignalInfo requestCompleted = new SignalInfo("request_completed", Integer.class, String.class);

    private FirebaseAuth mAuth;
    private FirebaseCrashlytics mCrashlytics;
    private FirebaseAnalytics mAnalytics;
    private FirebasePerformance mPerformance;

    private final OkHttpClient client = new OkHttpClient.Builder().connectTimeout(15L, TimeUnit.SECONDS).build();

    private final HashMap<Integer, HttpMetric> metrics = new HashMap<>();
    private final HashMap<Integer, Trace> traces = new HashMap<>();
    private final HashMap<Integer, Bundle> bundles = new HashMap<>();

    public FirebasePlugin(Godot godot) {
        super(godot);
        try {
            FirebaseApp.initializeApp(Objects.requireNonNull(getActivity()));
            mAuth = FirebaseAuth.getInstance();
            mCrashlytics = FirebaseCrashlytics.getInstance();
            mAnalytics = FirebaseAnalytics.getInstance(getActivity());
            mPerformance = FirebasePerformance.getInstance();
            Log.d(TAG, "Firebase initialized.");
        } catch (Exception e) {
            Log.e(TAG, "ERROR " + e.getMessage());
        }
    }

    @Override
    public String getPluginName() {
        return "Firebase";
    }

    @Override
    public Set<SignalInfo> getPluginSignals() {
        return new HashSet<>(Arrays.asList(
                loginSuccessfullySignal,
                loginFailedSignal,
                idTokenLoadedSignal,
                idTokenFailedSignal,
                requestCompleted));
    }

    @Override
    public List<String> getPluginMethods() {
        return Arrays.asList(
                "load",
                "load_library",
                "get_plugin_methods",
                "http_request",
                "login_with_play_games",
                "login_with_google",
                "is_logged_in",
                "get_user_name",
                "get_photo_url",
                "get_email",
                "get_uid",
                "get_id_token",
                "sign_out",

                "set_analytics_user_id",
                "set_analytics_user_property",
                "new_bundle",
                "put_bundle_string",
                "put_bundle_int",
                "put_bundle_float",
                "put_bundle_parcelable_array",
                "log_event_bundle",
                "log_event",

                "record_exception",
                "log",
                "log_set_custom_key",

                "new_http_metric",
                "http_metric_start",
                "http_metric_stop",
                "http_metric_set_http_response_code",
                "http_metric_set_set_request_payload_size",
                "http_metric_set_response_payload_size",
                "http_metric_set_response_content_type",
                "http_metric_mark_request_complete",
                "http_metric_mark_response_start",
                "http_metric_get_attribute",
                "http_metric_put_attribute",
                "http_metric_remove_attribute",
                "new_trace",
                "trace_start",
                "trace_stop",
                "trace_get_attribute",
                "trace_put_attribute",
                "trace_remove_attribute",
                "trace_increment_metric",
                "trace_get_long_metric",
                "trace_put_metric"
        );
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    public void load(String libname){
        System.load(libname);
    }

    public void load_library(String libname){
        System.loadLibrary(libname);
    }

    public String[] get_plugin_methods() {
        List<String> list = getPluginMethods();
        String[] array = new String[list.size()];
        for (int i = 0; i < list.size(); i++) array[i] = list.get(i);
        return array;
    }

    public void http_request(String url, String[] headers, String method, String body) {
        Request.Builder builder = new Request.Builder()
                .url(url);

        String contentType = "application/json";
        for (String header : headers) {
            String[] headerData = header.split(": ");
            if (headerData[0].toLowerCase().equals("content-type")) {
                contentType = headerData[1];
            }

            builder.addHeader(headerData[0], headerData[1]);
        }
        builder.method(method, body != null && method.equals("POST") ? RequestBody.create(MediaType.get(contentType), body) : null);

        Request request = builder.build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        emitSignal(requestCompleted.getName(), 0, e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            emitSignal(requestCompleted.getName(), response.code(), response.body().string());
                        } catch (Exception e) {
                            emitSignal(requestCompleted.getName(), 0, e.getMessage());
                        }
                    }
                });
            }
        });
    }

    public void login_with_play_games(String webClientid) {
        Log.d(TAG, "Request to login with play games" );
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN)
                .requestServerAuthCode(webClientid, true)
                .requestEmail()
                .build();

        GoogleSignInClient mGoogleSignInClient = GoogleSignIn.getClient(Objects.requireNonNull(getActivity()), gso);
        getGodot().startActivityForResult(mGoogleSignInClient.getSignInIntent(), RC_PLAY_GAMES_SIGN_IN);
    }

    public void login_with_google(String webClientid) {
        Log.d(TAG, "Request to login with google" );
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientid)
                .requestEmail()
                .build();

        GoogleSignInClient mGoogleSignInClient = GoogleSignIn.getClient(Objects.requireNonNull(getActivity()), gso);
        getGodot().startActivityForResult(mGoogleSignInClient.getSignInIntent(), RC_GOOGLE_SIGN_IN);
    }

    public boolean is_logged_in() {
        return mAuth.getCurrentUser() != null;
    }

    public String get_user_name() {
        return getCurrentUser().getDisplayName();
    }

    public String get_photo_url() {
        Uri url = getCurrentUser().getPhotoUrl();
        assert url != null;
        return url.toString();
    }

    public String get_email() {
        return getCurrentUser().getEmail();
    }

    public String get_uid() {
        return getCurrentUser().getUid();
    }

    public void get_id_token(boolean forceRefresh) {
        try{
            Log.d(TAG, "Request to get id token" );
            FirebaseUser user = getCurrentUser();
            if (user == null) {
                Log.d(TAG, "There is no logged user");
                emitSignal(idTokenFailedSignal.getName(), "User not loaded");
                return;
            }

            Log.d(TAG, "Identified user: " + user.getDisplayName());

            user
                    .getIdToken(forceRefresh)
                    .addOnCompleteListener(new OnCompleteListener<GetTokenResult>() {
                        public void onComplete(@NonNull Task<GetTokenResult> task) {
                            if (task.isSuccessful()) {
                                String idToken = Objects.requireNonNull(task.getResult()).getToken();
                                if (idToken != null) {
                                    emitSignal(idTokenLoadedSignal.getName(), idToken);
                                } else {
                                    emitSignal(idTokenFailedSignal.getName(), "Token Unavailable");
                                }
                            } else {
                                String msg = "Get Id Token in failed " + Objects.requireNonNull(task.getException()).getMessage();
                                Log.w(TAG, msg);
                                emitSignal(idTokenFailedSignal.getName(), msg);
                            }
                        }
                    });
        } catch (Exception e){
            Log.e(TAG, "Error in get_id_token: " + e.getMessage());
        }
    }

    public void sign_out() {
        mAuth.signOut();
    }

    public void set_analytics_user_id(String id){
        mAnalytics.setUserId(id);
    }

    public void set_analytics_user_property(String name, String value){
        mAnalytics.setUserProperty(name, value);
    }

    public int new_bundle(){
        Bundle bundle = new Bundle();

        for (int i = 0; i <= bundles.keySet().size(); i++) {
            if (!bundles.containsKey(i)) {
                bundles.put(i, bundle);
                return i;
            }
        }

        return -1;
    }

    public void put_bundle_string(int bundle_id, String param, String value){
        Bundle bundle = bundles.get(bundle_id);
        if (bundle != null) {
            bundle.putString(param, value);
        }
    }

    public void put_bundle_parcelable_array(int bundle_id, String param, int[] parcelable_bundles){
        Bundle bundle = bundles.get(bundle_id);
        if (bundle != null) {
            Parcelable[] parcelables = new Parcelable[parcelable_bundles.length];

            for (int i = 0; i < parcelable_bundles.length; i++){
                int index = parcelable_bundles[i];
                parcelables[i] = bundles.get(index);
                bundles.remove(index);
            }

            bundle.putParcelableArray(param, parcelables);
        }
    }

    public void put_bundle_float(int bundle_id, String param, float value){
        Bundle bundle = bundles.get(bundle_id);
        if (bundle != null) {
            bundle.putFloat(param, value);
        }
    }

    public void put_bundle_int(int bundle_id, String param, int value){
        Bundle bundle = bundles.get(bundle_id);
        if (bundle != null) {
            bundle.putInt(param, value);
        }
    }

    public void log_event_bundle(String event, int bundle_id){
        Bundle bundle = bundles.get(bundle_id);
        if (bundle != null) {
            mAnalytics.logEvent(event, bundle);
            bundles.remove(bundle_id);
        }
    }

    public void log_event(String event, String[] keys, String[] values) {
        if (keys.length != values.length) {
            return;
        }
        Bundle bundle = new Bundle();
        for (int i = 0; i < keys.length; i++) {
            bundle.putString(keys[i], values[i]);
        }
        mAnalytics.logEvent(event, bundle);
    }

    public void record_exception(String message) {
        mCrashlytics.recordException(new RuntimeException(message));
    }

    public void log(String message) {
        mCrashlytics.log(message);
    }

    public void log_set_custom_key(String key, String value) {
        mCrashlytics.setCustomKey(key, value);
    }

    public int new_http_metric(String url, String method) {
        HttpMetric metric = new HttpMetric(url, method, TransportManager.getInstance(), new Timer());

        for (int i = 0; i <= metrics.keySet().size(); i++) {
            if (!metrics.containsKey(i)) {
                metrics.put(i, metric);
                return i;
            }
        }

        return -1;
    }

    public void http_metric_start(int metric_id) {
        HttpMetric metric = metrics.get(metric_id);
        if (metric != null) {
            metric.start();
        }
    }

    public void http_metric_stop(int metric_id) {
        HttpMetric metric = metrics.get(metric_id);
        if (metric != null) {
            metric.stop();

            metrics.remove(metric_id);
        }
    }

    public void http_metric_set_http_response_code(int metric_id, int response_code) {
        HttpMetric metric = metrics.get(metric_id);
        if (metric != null) {
            metric.setHttpResponseCode(response_code);
        }
    }

    public void http_metric_set_set_request_payload_size(int metric_id, int bytes) {
        HttpMetric metric = metrics.get(metric_id);
        if (metric != null) {
            metric.setRequestPayloadSize((long) bytes);
        }
    }

    public void http_metric_set_response_payload_size(int metric_id, int bytes) {
        HttpMetric metric = metrics.get(metric_id);
        if (metric != null) {
            metric.setResponsePayloadSize((long) bytes);
        }
    }

    public void http_metric_set_response_content_type(int metric_id, String contet_type) {
        HttpMetric metric = metrics.get(metric_id);
        if (metric != null) {
            metric.setResponseContentType(contet_type);
        }
    }

    public void http_metric_mark_request_complete(int metric_id) {
        HttpMetric metric = metrics.get(metric_id);
        if (metric != null) {
            metric.markRequestComplete();
        }
    }

    public void http_metric_mark_response_start(int metric_id) {
        HttpMetric metric = metrics.get(metric_id);
        if (metric != null) {
            metric.markResponseStart();
        }
    }

    public String http_metric_get_attribute(int metric_id, String attribute) {
        HttpMetric metric = metrics.get(metric_id);
        return metric != null ? metric.getAttribute(attribute) : null;
    }

    public void http_metric_put_attribute(int metric_id, String attribute, String value) {
        HttpMetric metric = metrics.get(metric_id);
        if (metric != null) {
            metric.putAttribute(attribute, value);
        }
    }

    public void http_metric_remove_attribute(int metric_id, String attribute) {
        HttpMetric metric = metrics.get(metric_id);
        if (metric != null) {
            metric.removeAttribute(attribute);
        }
    }


    public int new_trace(String trace_name) {
        Trace trace = mPerformance.newTrace(trace_name);

        for (int i = 0; i <= traces.keySet().size(); i++) {
            if (!traces.containsKey(i)) {
                traces.put(i, trace);
                return i;
            }
        }

        return -1;
    }

    public void trace_start(int trace_id) {
        Trace trace = traces.get(trace_id);
        if (trace != null) {
            trace.start();
        }
    }

    public void trace_stop(int trace_id) {
        Trace trace = traces.get(trace_id);
        if (trace != null) {
            trace.stop();
            traces.remove(trace_id);
        }
    }

    public String trace_get_attribute(int trace_id, String attribute) {
        Trace trace = traces.get(trace_id);
        return trace != null ? trace.getAttribute(attribute) : null;
    }

    public void trace_put_attribute(int trace_id, String attribute, String value) {
        Trace trace = traces.get(trace_id);
        if (trace != null) {
            trace.putAttribute(attribute, value);
        }
    }

    public void trace_remove_attribute(int trace_id, String attribute) {
        Trace trace = traces.get(trace_id);
        if (trace != null) {
            trace.removeAttribute(attribute);
        }
    }

    public void trace_increment_metric(int trace_id, String metric, int increment_value) {
        Trace trace = traces.get(trace_id);
        if (trace != null) {
            trace.incrementMetric(metric, (long) increment_value);
        }
    }

    public int trace_get_long_metric(int trace_id, String metric) {
        Trace trace = traces.get(trace_id);
        return (int) (trace != null ? trace.getLongMetric(metric) : 0);
    }

    public void trace_put_metric(int trace_id, String metric, int value) {
        Trace trace = traces.get(trace_id);
        if (trace != null) {
            trace.putMetric(metric, (long) value);
        }
    }

    private FirebaseUser getCurrentUser() {
        return mAuth.getCurrentUser();
    }

    @Override
    public void onMainActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_PLAY_GAMES_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            assert result != null;
            if (result.isSuccess()) {
                GoogleSignInAccount account = result.getSignInAccount();
                assert account != null;
                Log.d(TAG, "firebaseAuthWithPlayGames:" + account.getId());
                AuthCredential credential = PlayGamesAuthProvider.getCredential(Objects.requireNonNull(account.getServerAuthCode()));
                firebaseAuth(credential);
            } else {
                String message = result.getStatus().getStatusCode() + ": " + result.getStatus().getStatusMessage();
                Log.w(TAG, "Play Games sign in failed " + message);
                emitSignal(loginFailedSignal.getName(), message);
            }
        } else if (requestCode == RC_GOOGLE_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                assert account != null;
                Log.d(TAG, "firebaseAuthWithGoogle:" + account.getId());
                AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
                firebaseAuth(credential);
            } catch (ApiException e) {
                Log.w(TAG, "Google sign in failed", e);
                emitSignal(loginFailedSignal.getName(), e.getMessage());
            }
        }
    }

    private void firebaseAuth(AuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(Objects.requireNonNull(getActivity()), new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "signInWithCredential:success");
                            FirebaseUser user = mAuth.getCurrentUser();
                            assert user != null;
                            mCrashlytics.setUserId(user.getUid());
                            mAnalytics.setUserId(user.getUid());
                            mCrashlytics.sendUnsentReports();
                            emitSignal(loginSuccessfullySignal.getName());
                        } else {
                            Log.w(TAG, "signInWithCredential:failure", task.getException());
                            emitSignal(loginFailedSignal.getName(), Objects.requireNonNull(task.getException()).getMessage());
                        }
                    }
                });
    }
}
