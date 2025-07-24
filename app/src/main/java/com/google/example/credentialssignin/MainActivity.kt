package com.google.example.credentialssignin

import android.app.ProgressDialog
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.credentials.Credential
import com.google.android.gms.auth.api.credentials.CredentialRequest
import com.google.android.gms.auth.api.credentials.Credentials
import com.google.android.gms.auth.api.credentials.CredentialsClient
import com.google.android.gms.auth.api.credentials.IdentityProviders
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task

class MainActivity : AppCompatActivity(), View.OnClickListener {
    private var mCredentialsClient: CredentialsClient? = null
    private var mSignInClient: GoogleSignInClient? = null
    private var mProgressDialog: ProgressDialog? = null
    private var mIsResolving = false
    private var mCredential: Credential? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState != null) {
            mIsResolving = savedInstanceState.getBoolean(KEY_IS_RESOLVING, false)
            mCredential = savedInstanceState.getParcelable(KEY_CREDENTIAL)
        }

        // Build CredentialsClient and GoogleSignInClient, don't set account name
        buildClients(null)

        // Sign in button
        val signInButton = findViewById<View>(R.id.button_google_sign_in) as SignInButton
        signInButton.setSize(SignInButton.SIZE_WIDE)
        signInButton.setOnClickListener(this)

        // Other buttons
        findViewById<View>(R.id.button_email_sign_in).setOnClickListener(this)
        findViewById<View>(R.id.button_google_revoke).setOnClickListener(this)
        findViewById<View>(R.id.button_google_sign_out).setOnClickListener(this)
        findViewById<View>(R.id.button_email_save).setOnClickListener(this)
    }

    private fun buildClients(accountName: String?) {
        Log.d(TAG, "buildClients: ... 0 ...")
        val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()

        if (accountName != null) {
            Log.d(TAG, "buildClients: ... 1 ...")
            gsoBuilder.setAccountName(accountName)
        }
        Log.d(TAG, "buildClients: ... 2 ...: Initializing mCredentialsClient and mSignInClient ..")
        mCredentialsClient = Credentials.getClient(this)
        mSignInClient = GoogleSignIn.getClient(this, gsoBuilder.build())
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_IS_RESOLVING, mIsResolving)
        outState.putParcelable(KEY_CREDENTIAL, mCredential)
    }

    public override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: .. 1 .. ")
        if (!mIsResolving) {
            Log.d(TAG, "onStart: .. 2 ..")
            requestCredentials(true,  /* shouldResolve */false /* onlyPasswords */)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(
            TAG,
            "onActivityResult:$requestCode:$resultCode:$data"
        )

        if (requestCode == RC_SIGN_IN) {
            Log.d(TAG, "onActivityResult: ... 1 ...")
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleGoogleSignIn(task)
        } else if (requestCode == RC_CREDENTIALS_READ) {
            Log.d(TAG, "onActivityResult: ... 2 ...")
            mIsResolving = false
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "onActivityResult: ... 3 ...")
                val credential = data?.getParcelableExtra<Credential>(Credential.EXTRA_KEY)
                handleCredential(credential!!)
            }
            Log.d(TAG, "onActivityResult: ... 4 ...")
        } else if (requestCode == RC_CREDENTIALS_SAVE) {
            Log.d(TAG, "onActivityResult: ... 5 ...")
            mIsResolving = false
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "onActivityResult: ... 6 ...")
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            } else {
                Log.d(TAG, "onActivityResult: ... 7 ...")
                Log.w(TAG, "Credential save failed.")
            }
        }
    }

    private fun googleSilentSignIn() {
        Log.d(TAG, "googleSilentSignIn: ... a ...")
        // Try silent sign-in with Google Sign In API
        val silentSignIn = mSignInClient!!.silentSignIn()
        Log.d(TAG, "googleSilentSignIn: ... b ...")
        if (silentSignIn.isComplete && silentSignIn.isSuccessful) {
            Log.d(TAG, "googleSilentSignIn: ... c ...")
            handleGoogleSignIn(silentSignIn)
            return
        }

        Log.d(TAG, "googleSilentSignIn: ... d ...")
        showProgress()
        silentSignIn.addOnCompleteListener { task ->
            Log.d(TAG, "googleSilentSignIn: ... d ...")
            hideProgress()
            handleGoogleSignIn(task)
        }
    }

    private fun handleCredential(credential: Credential) {
        Log.d(TAG, "handleCredential: ... 1 ...")
        mCredential = credential

        Log.d(TAG, "handleCredential:" + credential.accountType + ":" + credential.id)
        if (IdentityProviders.GOOGLE == credential.accountType) {
            Log.d(TAG, "handleCredential: ... 2 ...")
            // Google account, rebuild GoogleApiClient to set account name and then try
            buildClients(credential.id)
            googleSilentSignIn()
            Log.d(TAG, "handleCredential: ... 3 ...")
        } else {
            Log.d(TAG, "handleCredential: ... 4 ...")
            // Email/password account
            val status = String.format("Signed in as %s", credential.id)
            (findViewById<View>(R.id.text_email_status) as TextView).text = status
        }
    }

    private fun handleGoogleSignIn(completedTask: Task<GoogleSignInAccount>?) {
        Log.d(TAG, "handleGoogleSignIn:$completedTask")

        val isSignedIn = (completedTask != null) && completedTask.isSuccessful
        if (isSignedIn) {
            Log.d(TAG, "handleGoogleSignIn: ... A ...")
            // Display signed-in UI
            val gsa = completedTask!!.result
            val status = String.format(
                "Signed in as %s (%s)", gsa!!.displayName,
                gsa.email
            )
            (findViewById<View>(R.id.text_google_status) as TextView).text = status
            Log.d(TAG, "handleGoogleSignIn: ... B ...")
            // Save Google Sign In to SmartLock
            val credential = Credential.Builder(
                gsa.email
            )
                .setAccountType(IdentityProviders.GOOGLE)
                .setName(gsa.displayName)
                .setProfilePictureUri(gsa.photoUrl)
                .build()
            Log.d(TAG, "handleGoogleSignIn: ... C ...")
            saveCredential(credential)
        } else {
            Log.d(TAG, "handleGoogleSignIn: ... D ...")
            // Display signed-out UI
            (findViewById<View>(R.id.text_google_status) as TextView).setText(R.string.signed_out)
        }
        Log.d(TAG, "handleGoogleSignIn: ... E ...")
        findViewById<View>(R.id.button_google_sign_in).isEnabled = !isSignedIn
        findViewById<View>(R.id.button_google_sign_out).isEnabled = isSignedIn
        findViewById<View>(R.id.button_google_revoke).isEnabled = isSignedIn
    }

    private fun requestCredentials(shouldResolve: Boolean, onlyPasswords: Boolean) {
        Log.d(TAG, "requestCredentials:  ... 1 ...")
        val crBuilder = CredentialRequest.Builder()
            .setPasswordLoginSupported(true)

        if (!onlyPasswords) {
            Log.d(TAG, "requestCredentials:  ... 2 ...")
            crBuilder.setAccountTypes(IdentityProviders.GOOGLE)
        }
        Log.d(TAG, "requestCredentials:  ... 3 ...")
        showProgress()
        Log.d(TAG, "requestCredentials:  ... 4 ...")
        mCredentialsClient!!.request(crBuilder.build()).addOnCompleteListener(
            OnCompleteListener { task ->
                Log.d(TAG, "requestCredentials:  ... 5 ...")
                hideProgress()

                if (task.isSuccessful) {
                    Log.d(TAG, "requestCredentials:  ... 6 ...")
                    // Auto sign-in success
                    handleCredential(task.result!!.credential!!)
                    return@OnCompleteListener
                }

                Log.d(TAG, "requestCredentials:  ... 7 ...")
                val e = task.exception
                if (e is ResolvableApiException && shouldResolve) {
                    Log.d(TAG, "requestCredentials:  ... 8 ...")
                    // Getting credential needs to show some UI, start resolution
                    resolveResult(e, RC_CREDENTIALS_READ)
                } else {
                    Log.d(TAG, "requestCredentials:  ... 9 ...")
                    Log.w(
                        TAG,
                        "request: not handling exception",
                        e
                    )
                }
            })
    }

    private fun resolveResult(rae: ResolvableApiException, requestCode: Int) {
        Log.d(TAG, "resolveResult: ... aa ...")
        if (!mIsResolving) {
            Log.d(TAG, "resolveResult: ... bb ...")
            try {
                Log.d(TAG, "resolveResult: ... cc ...")
                rae.startResolutionForResult(this@MainActivity, requestCode)
                mIsResolving = true
            } catch (e: SendIntentException) {
                Log.d(TAG, "resolveResult: ... dd ...")
                Log.e(TAG, "Failed to send Credentials intent.", e)
                mIsResolving = false
            }
        }
    }

    private fun saveCredential(credential: Credential?) {
        Log.d(TAG, "saveCredential: ... 1 ...")
        if (credential == null) {
            Log.d(TAG, "saveCredential: ... 2 ...")
            Log.w(TAG, "Ignoring null credential.")
            return
        }
        Log.d(TAG, "saveCredential: ... 3 ...")
        mCredentialsClient!!.save(mCredential!!).addOnCompleteListener(
            OnCompleteListener { task ->
                Log.d(TAG, "saveCredential: ... 4 ...")
                if (task.isSuccessful) {
                    Log.d(TAG, "saveCredential: ... 5 ...")
                    Log.d(TAG, "save:SUCCESS")
                    return@OnCompleteListener
                }
                Log.d(TAG, "saveCredential: ... 6 ...")
                val e = task.exception
                if (e is ResolvableApiException) {
                    Log.d(TAG, "saveCredential: ... 7 ...")
                    // Saving the credential can sometimes require showing some UI
                    // to the user, which means we need to fire this resolution.
                    resolveResult(e, RC_CREDENTIALS_SAVE)
                } else {
                    Log.d(TAG, "saveCredential: ... 8 ...")
                    Log.w(TAG, "save:FAILURE", e)
                    Toast.makeText(
                        this@MainActivity,
                        "Unexpected error, see logs for detals",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun onGoogleSignInClicked() {
        Log.d(TAG, "onGoogleSignInClicked: startActivityForResult()....")
        val intent = mSignInClient!!.signInIntent
        startActivityForResult(intent, RC_SIGN_IN)
    }

    private fun onGoogleRevokeClicked() {
        Log.d(TAG, "onGoogleRevokeClicked: ... 1 ...")
        if (mCredential != null) {
            Log.d(TAG, "onGoogleRevokeClicked: ... 2 ...")
            mCredentialsClient!!.delete(mCredential!!)
        }

        Log.d(TAG, "onGoogleRevokeClicked: ... 3 ...")
        mSignInClient!!.revokeAccess().addOnCompleteListener {
            Log.d(TAG, "onGoogleRevokeClicked: ... 4 ...")
            handleGoogleSignIn(null)
        }
    }

    private fun onGoogleSignOutClicked() {
        Log.d(TAG, "onGoogleSignOutClicked: ...1...")
        mCredentialsClient!!.disableAutoSignIn()
        Log.d(TAG, "onGoogleSignOutClicked: ...2...")
        mSignInClient!!.signOut().addOnCompleteListener {
            Log.d(TAG, "onGoogleSignOutClicked: ...3...")
            handleGoogleSignIn(null)
        }
    }

    private fun onEmailSignInClicked() {
        Log.d(TAG, "onEmailSignInClicked: ... - ...")
        requestCredentials(true, true)
    }

    private fun onEmailSaveClicked() {
        Log.d(TAG, "onEmailSaveClicked: ... A ...")
        val email = (findViewById<View>(R.id.edit_text_email) as EditText).text.toString()
        val password = (findViewById<View>(R.id.edit_text_password) as EditText).text.toString()

        if (email.length == 0 || password.length == 0) {
            Log.d(TAG, "onEmailSaveClicked: ... B ...")
            Log.w(TAG, "Blank email or password, can't save Credential.")
            Toast.makeText(this, "Email/Password must not be blank.", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "onEmailSaveClicked: ... C ...")
        val credential = Credential.Builder(email)
            .setPassword(password)
            .build()

        Log.d(TAG, "onEmailSaveClicked: ... D ...")
        saveCredential(credential)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.button_google_sign_in -> {
                Log.d(TAG, "onClick: onGoogleSignInClicked")
                onGoogleSignInClicked()
            }

            R.id.button_google_revoke -> {
                Log.d(TAG, "onClick: onGoogleRevokeClicked")
                onGoogleRevokeClicked()
            }

            R.id.button_google_sign_out -> {
                Log.d(TAG, "onClick: onGoogleSignOutClicked")
                onGoogleSignOutClicked()
            }

            R.id.button_email_sign_in -> {
                Log.d(TAG, "onClick: onEmailSignInClicked")
                onEmailSignInClicked()
            }

            R.id.button_email_save -> {
                Log.d(TAG, "onClick: onEmailSaveClicked")
                onEmailSaveClicked()
            }
        }
    }

    private fun showProgress() {
        Log.d(TAG, "showProgress: ..1 ..")
        if (mProgressDialog == null) {
            Log.d(TAG, "showProgress: ..2 ..")
            mProgressDialog = ProgressDialog(this)
            mProgressDialog!!.isIndeterminate = true
            mProgressDialog!!.setMessage("Loading...")
        }
        Log.d(TAG, "showProgress: ..3 ..")
        mProgressDialog!!.show()
    }

    private fun hideProgress() {
        Log.d(TAG, "hideProgress: .. 1 ..")
        if (mProgressDialog != null && mProgressDialog!!.isShowing) {
            Log.d(TAG, "hideProgress: .. 2 ..")
            mProgressDialog!!.dismiss()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val KEY_IS_RESOLVING = "is_resolving"
        private const val KEY_CREDENTIAL = "key_credential"
        private const val KEY_CREDENTIAL_TO_SAVE = "key_credential_to_save"

        private const val RC_SIGN_IN = 1
        private const val RC_CREDENTIALS_READ = 2
        private const val RC_CREDENTIALS_SAVE = 3
    }
}
