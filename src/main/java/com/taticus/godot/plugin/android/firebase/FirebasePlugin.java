package com.taticus.godot.plugin.android.firebase;

import android.content.Intent;
import android.net.Uri;
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
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.PlayGamesAuthProvider;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;

public class FirebasePlugin extends GodotPlugin {

    private static final String TAG = "FirebasePlugin";
    private static final int RC_PLAY_GAMES_SIGN_IN = 9001;
    private static final int RC_GOOGLE_SIGN_IN = 9002;

    private final SignalInfo loginSuccessSignal = new SignalInfo("login_success");
    private final SignalInfo loginErrorSignal = new SignalInfo("login_error", String.class);

    private FirebaseAuth mAuth;

    public FirebasePlugin(Godot godot) {
        super(godot);
        try {
            FirebaseApp.initializeApp(godot);
            mAuth = FirebaseAuth.getInstance();
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
        return new HashSet<>(Arrays.asList(loginSuccessSignal, loginErrorSignal));
    }

    @Override
    public List<String> getPluginMethods() {
        return Arrays.asList(
                "login_with_play_games",
                "login_with_google",
                "is_logged_in",
                "get_user_name",
                "get_photo_url",
                "get_email",
                "get_uid",
                "get_id_token",
                "sign_out"
        );
    }

    public void login_with_play_games(String webClientid) {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN)
                .requestServerAuthCode(webClientid, true)
                .requestEmail()
                .build();

        GoogleSignInClient mGoogleSignInClient = GoogleSignIn.getClient(getGodot(), gso);
        getGodot().startActivityForResult(mGoogleSignInClient.getSignInIntent(), RC_PLAY_GAMES_SIGN_IN);
    }

    public void login_with_google(String webClientid) {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientid)
                .requestEmail()
                .build();

        GoogleSignInClient mGoogleSignInClient = GoogleSignIn.getClient(getGodot(), gso);
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

    public String get_id_token(boolean forceRefresh) {
        return Objects.requireNonNull(getCurrentUser().getIdToken(forceRefresh).getResult()).getToken();
    }

    public void sign_out() {
        mAuth.signOut();
    }

    @NonNull
    private FirebaseUser getCurrentUser() {
        FirebaseUser user = mAuth.getCurrentUser();
        assert user != null;
        return user;
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
                emitSignal(loginErrorSignal.getName(), message);
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
                emitSignal(loginErrorSignal.getName(), e.getMessage());
            }
        }
    }

    private void firebaseAuth(AuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(getGodot(), new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "signInWithCredential:success");
                            FirebaseUser user = mAuth.getCurrentUser();
                            assert user != null;
                            emitSignal(loginSuccessSignal.getName());
                        } else {
                            Log.w(TAG, "signInWithCredential:failure", task.getException());
                            emitSignal(loginErrorSignal.getName(), Objects.requireNonNull(task.getException()).getMessage());
                        }
                    }
                });
    }
}
