package com.google.example.credentialssignin;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.credentials.Credential;
import com.google.android.gms.auth.api.credentials.CredentialRequest;
import com.google.android.gms.auth.api.credentials.CredentialRequestResponse;
import com.google.android.gms.auth.api.credentials.Credentials;
import com.google.android.gms.auth.api.credentials.CredentialsClient;
import com.google.android.gms.auth.api.credentials.IdentityProviders;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

public class MainActivity extends AppCompatActivity implements
        View.OnClickListener {

    private static final String TAG = "MainActivity";
    private static final String KEY_IS_RESOLVING = "is_resolving";
    private static final String KEY_CREDENTIAL = "key_credential";
    private static final String KEY_CREDENTIAL_TO_SAVE = "key_credential_to_save";

    private static final int RC_SIGN_IN = 1;
    private static final int RC_CREDENTIALS_READ = 2;
    private static final int RC_CREDENTIALS_SAVE = 3;

    private CredentialsClient mCredentialsClient;
    private GoogleSignInClient mSignInClient;
    private ProgressDialog mProgressDialog;
    private boolean mIsResolving = false;
    private Credential mCredential;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState != null) {
            mIsResolving = savedInstanceState.getBoolean(KEY_IS_RESOLVING, false);
            mCredential = savedInstanceState.getParcelable(KEY_CREDENTIAL);
        }

        // Build CredentialsClient and GoogleSignInClient, don't set account name
        buildClients(null);

        // Sign in button
        SignInButton signInButton = (SignInButton) findViewById(R.id.button_google_sign_in);
        signInButton.setSize(SignInButton.SIZE_WIDE);
        signInButton.setOnClickListener(this);

        // Other buttons
        findViewById(R.id.button_email_sign_in).setOnClickListener(this);
        findViewById(R.id.button_google_revoke).setOnClickListener(this);
        findViewById(R.id.button_google_sign_out).setOnClickListener(this);
        findViewById(R.id.button_email_save).setOnClickListener(this);
    }

    private void buildClients(String accountName) {
        Log.d(TAG, "buildClients: ... 0 ...");
        GoogleSignInOptions.Builder gsoBuilder = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail();

        if (accountName != null) {
            Log.d(TAG, "buildClients: ... 1 ...");
            gsoBuilder.setAccountName(accountName);
        }
        Log.d(TAG, "buildClients: ... 2 ...: Initializing mCredentialsClient and mSignInClient ..");
        mCredentialsClient = Credentials.getClient(this);
        mSignInClient = GoogleSignIn.getClient(this, gsoBuilder.build());
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_IS_RESOLVING, mIsResolving);
        outState.putParcelable(KEY_CREDENTIAL, mCredential);
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart: .. 1 .. ");
        if (!mIsResolving) {
            Log.d(TAG, "onStart: .. 2 ..");
            requestCredentials(true /* shouldResolve */, false /* onlyPasswords */);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult:" + requestCode + ":" + resultCode + ":" + data);

        if (requestCode == RC_SIGN_IN) {
            Log.d(TAG, "onActivityResult: ... 1 ...");
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleGoogleSignIn(task);
        } else if (requestCode == RC_CREDENTIALS_READ) {
            Log.d(TAG, "onActivityResult: ... 2 ...");
            mIsResolving = false;
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "onActivityResult: ... 3 ...");
                Credential credential = data.getParcelableExtra(Credential.EXTRA_KEY);
                handleCredential(credential);
            }
            Log.d(TAG, "onActivityResult: ... 4 ...");
        } else if (requestCode == RC_CREDENTIALS_SAVE) {
            Log.d(TAG, "onActivityResult: ... 5 ...");
            mIsResolving = false;
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "onActivityResult: ... 6 ...");
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
            } else {
                Log.d(TAG, "onActivityResult: ... 7 ...");
                Log.w(TAG, "Credential save failed.");
            }
        }
    }

    private void googleSilentSignIn() {
        Log.d(TAG, "googleSilentSignIn: ... a ...");
        // Try silent sign-in with Google Sign In API
        Task<GoogleSignInAccount> silentSignIn = mSignInClient.silentSignIn();
        Log.d(TAG, "googleSilentSignIn: ... b ...");
        if (silentSignIn.isComplete() && silentSignIn.isSuccessful()) {
            Log.d(TAG, "googleSilentSignIn: ... c ...");
            handleGoogleSignIn(silentSignIn);
            return;
        }

        Log.d(TAG, "googleSilentSignIn: ... d ...");
        showProgress();
        silentSignIn.addOnCompleteListener(new OnCompleteListener<GoogleSignInAccount>() {
            @Override
            public void onComplete(@NonNull Task<GoogleSignInAccount> task) {
                Log.d(TAG, "googleSilentSignIn: ... d ...");
                hideProgress();
                handleGoogleSignIn(task);
            }
        });
    }

    private void handleCredential(Credential credential) {
        Log.d(TAG, "handleCredential: ... 1 ...");
        mCredential = credential;

        Log.d(TAG, "handleCredential:" + credential.getAccountType() + ":" + credential.getId());
        if (IdentityProviders.GOOGLE.equals(credential.getAccountType())) {
            Log.d(TAG, "handleCredential: ... 2 ...");
            // Google account, rebuild GoogleApiClient to set account name and then try
            buildClients(credential.getId());
            googleSilentSignIn();
            Log.d(TAG, "handleCredential: ... 3 ...");
        } else {
            Log.d(TAG, "handleCredential: ... 4 ...");
            // Email/password account
            String status = String.format("Signed in as %s", credential.getId());
            ((TextView) findViewById(R.id.text_email_status)).setText(status);
        }
    }

    private void handleGoogleSignIn(Task<GoogleSignInAccount> completedTask) {
        Log.d(TAG, "handleGoogleSignIn:" + completedTask);

        boolean isSignedIn = (completedTask != null) && completedTask.isSuccessful();
        if (isSignedIn) {
            Log.d(TAG, "handleGoogleSignIn: ... A ...");
            // Display signed-in UI
            GoogleSignInAccount gsa = completedTask.getResult();
            String status = String.format("Signed in as %s (%s)", gsa.getDisplayName(),
                    gsa.getEmail());
            ((TextView) findViewById(R.id.text_google_status)).setText(status);
            Log.d(TAG, "handleGoogleSignIn: ... B ...");
            // Save Google Sign In to SmartLock
            Credential credential = new Credential.Builder(gsa.getEmail())
                    .setAccountType(IdentityProviders.GOOGLE)
                    .setName(gsa.getDisplayName())
                    .setProfilePictureUri(gsa.getPhotoUrl())
                    .build();
            Log.d(TAG, "handleGoogleSignIn: ... C ...");
            saveCredential(credential);
        } else {
            Log.d(TAG, "handleGoogleSignIn: ... D ...");
            // Display signed-out UI
            ((TextView) findViewById(R.id.text_google_status)).setText(R.string.signed_out);
        }
        Log.d(TAG, "handleGoogleSignIn: ... E ...");
        findViewById(R.id.button_google_sign_in).setEnabled(!isSignedIn);
        findViewById(R.id.button_google_sign_out).setEnabled(isSignedIn);
        findViewById(R.id.button_google_revoke).setEnabled(isSignedIn);
    }

    private void requestCredentials(final boolean shouldResolve, boolean onlyPasswords) {
        Log.d(TAG, "requestCredentials:  ... 1 ...");
        CredentialRequest.Builder crBuilder = new CredentialRequest.Builder()
                .setPasswordLoginSupported(true);

        if (!onlyPasswords) {
            Log.d(TAG, "requestCredentials:  ... 2 ...");
            crBuilder.setAccountTypes(IdentityProviders.GOOGLE);
        }
        Log.d(TAG, "requestCredentials:  ... 3 ...");
        showProgress();
        Log.d(TAG, "requestCredentials:  ... 4 ...");
        mCredentialsClient.request(crBuilder.build()).addOnCompleteListener(
                new OnCompleteListener<CredentialRequestResponse>() {
                    @Override
                    public void onComplete(@NonNull Task<CredentialRequestResponse> task) {
                        Log.d(TAG, "requestCredentials:  ... 5 ...");
                        hideProgress();

                        if (task.isSuccessful()) {
                            Log.d(TAG, "requestCredentials:  ... 6 ...");
                            // Auto sign-in success
                            handleCredential(task.getResult().getCredential());
                            return;
                        }

                        Log.d(TAG, "requestCredentials:  ... 7 ...");
                        Exception e = task.getException();

                        if (e instanceof ResolvableApiException && shouldResolve) {
                            Log.d(TAG, "requestCredentials:  ... 8 ...");
                            // Getting credential needs to show some UI, start resolution
                            ResolvableApiException rae = (ResolvableApiException) e;
                            resolveResult(rae, RC_CREDENTIALS_READ);
                        } else {
                            Log.d(TAG, "requestCredentials:  ... 9 ...");
                            Log.w(TAG, "request: not handling exception", e);
                        }
                    }
                });
    }

    private void resolveResult(ResolvableApiException rae, int requestCode) {
        Log.d(TAG, "resolveResult: ... aa ...");
        if (!mIsResolving) {
            Log.d(TAG, "resolveResult: ... bb ...");
            try {
                Log.d(TAG, "resolveResult: ... cc ...");
                rae.startResolutionForResult(MainActivity.this, requestCode);
                mIsResolving = true;
            } catch (IntentSender.SendIntentException e) {
                Log.d(TAG, "resolveResult: ... dd ...");
                Log.e(TAG, "Failed to send Credentials intent.", e);
                mIsResolving = false;
            }
        }
    }

    private void saveCredential(Credential credential) {
        Log.d(TAG, "saveCredential: ... 1 ...");
        if (credential == null) {
            Log.d(TAG, "saveCredential: ... 2 ...");
            Log.w(TAG, "Ignoring null credential.");
            return;
        }
        Log.d(TAG, "saveCredential: ... 3 ...");
        mCredentialsClient.save(mCredential).addOnCompleteListener(
                new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        Log.d(TAG, "saveCredential: ... 4 ...");
                        if (task.isSuccessful()) {
                            Log.d(TAG, "saveCredential: ... 5 ...");
                            Log.d(TAG, "save:SUCCESS");
                            return;
                        }
                        Log.d(TAG, "saveCredential: ... 6 ...");
                        Exception e = task.getException();
                        if (e instanceof ResolvableApiException) {
                            Log.d(TAG, "saveCredential: ... 7 ...");
                            // Saving the credential can sometimes require showing some UI
                            // to the user, which means we need to fire this resolution.
                            ResolvableApiException rae = (ResolvableApiException) e;
                            resolveResult(rae, RC_CREDENTIALS_SAVE);
                        } else {
                            Log.d(TAG, "saveCredential: ... 8 ...");
                            Log.w(TAG, "save:FAILURE", e);
                            Toast.makeText(MainActivity.this,
                                    "Unexpected error, see logs for detals",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void onGoogleSignInClicked() {
        Log.d(TAG, "onGoogleSignInClicked: startActivityForResult()....");
        Intent intent = mSignInClient.getSignInIntent();
        startActivityForResult(intent, RC_SIGN_IN);
    }

    private void onGoogleRevokeClicked() {
        Log.d(TAG, "onGoogleRevokeClicked: ... 1 ...");
        if (mCredential != null) {
            Log.d(TAG, "onGoogleRevokeClicked: ... 2 ...");
            mCredentialsClient.delete(mCredential);
        }

        Log.d(TAG, "onGoogleRevokeClicked: ... 3 ...");
        mSignInClient.revokeAccess().addOnCompleteListener(
                new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        Log.d(TAG, "onGoogleRevokeClicked: ... 4 ...");
                        handleGoogleSignIn(null);
                    }
                });
    }

    private void onGoogleSignOutClicked() {
        Log.d(TAG, "onGoogleSignOutClicked: ...1...");
        mCredentialsClient.disableAutoSignIn();
        Log.d(TAG, "onGoogleSignOutClicked: ...2...");
        mSignInClient.signOut().addOnCompleteListener(
                new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        Log.d(TAG, "onGoogleSignOutClicked: ...3...");
                        handleGoogleSignIn(null);
                    }
                });
    }

    private void onEmailSignInClicked() {
        Log.d(TAG, "onEmailSignInClicked: ... - ...");
        requestCredentials(true, true);
    }

    private void onEmailSaveClicked() {
        Log.d(TAG, "onEmailSaveClicked: ... A ...");
        String email = ((EditText) findViewById(R.id.edit_text_email)).getText().toString();
        String password = ((EditText) findViewById(R.id.edit_text_password)).getText().toString();

        if (email.length() == 0|| password.length() == 0) {
            Log.d(TAG, "onEmailSaveClicked: ... B ...");
            Log.w(TAG, "Blank email or password, can't save Credential.");
            Toast.makeText(this, "Email/Password must not be blank.", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "onEmailSaveClicked: ... C ...");
        Credential credential = new Credential.Builder(email)
                .setPassword(password)
                .build();

        Log.d(TAG, "onEmailSaveClicked: ... D ...");
        saveCredential(credential);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_google_sign_in:
                Log.d(TAG, "onClick: onGoogleSignInClicked");
                onGoogleSignInClicked();
                break;
            case R.id.button_google_revoke:
                Log.d(TAG, "onClick: onGoogleRevokeClicked");
                onGoogleRevokeClicked();
                break;
            case R.id.button_google_sign_out:
                Log.d(TAG, "onClick: onGoogleSignOutClicked");
                onGoogleSignOutClicked();
                break;
            case R.id.button_email_sign_in:
                Log.d(TAG, "onClick: onEmailSignInClicked");
                onEmailSignInClicked();
                break;
            case R.id.button_email_save:
                Log.d(TAG, "onClick: onEmailSaveClicked");
                onEmailSaveClicked();
                break;
        }
    }

    private void showProgress() {
        Log.d(TAG, "showProgress: ..1 ..");
        if (mProgressDialog == null) {
            Log.d(TAG, "showProgress: ..2 ..");
            mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setMessage("Loading...");
        }
        Log.d(TAG, "showProgress: ..3 ..");
        mProgressDialog.show();
    }

    private void hideProgress() {
        Log.d(TAG, "hideProgress: .. 1 ..");
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            Log.d(TAG, "hideProgress: .. 2 ..");
            mProgressDialog.dismiss();
        }
    }
}
