package com.taticus.godot.plugin.android.firebase;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
                "is_logged_in",
                "user_name",
                "photo_url",
                "email",
                "uid",
                "sign_out"
        );
    }

    public void login_with_play_games(String webClientid) {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientid)
                .requestEmail()
                .build();

        GoogleSignInClient mGoogleSignInClient = GoogleSignIn.getClient(getGodot(), gso);
        getGodot().startActivityForResult(mGoogleSignInClient.getSignInIntent(), RC_PLAY_GAMES_SIGN_IN);
    }

    public boolean is_logged_in() {
        return mAuth.getCurrentUser() != null;
    }

    public String user_name() {
        return getCurrentUser().getDisplayName();
    }

    public String photo_url() {
        Uri url = getCurrentUser().getPhotoUrl();
        assert url != null;
        return url.toString();
    }

    public String email() {
        return getCurrentUser().getEmail();
    }

    public String uid() {
        return getCurrentUser().getUid();
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
        if (requestCode == RC_PLAY_GAMES_SIGN_IN || requestCode == RC_GOOGLE_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                assert account != null;
                Log.d(TAG, "firebaseAuthWithGoogle:" + account.getId());
                if(requestCode == RC_GOOGLE_SIGN_IN){
                    AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
                    firebaseAuth(credential);
                } else {
                    AuthCredential credential = PlayGamesAuthProvider.getCredential(Objects.requireNonNull(account.getIdToken()));
                    firebaseAuth(credential);
                }
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
