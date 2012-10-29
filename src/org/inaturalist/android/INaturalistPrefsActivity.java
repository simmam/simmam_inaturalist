package org.inaturalist.android;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class INaturalistPrefsActivity extends Activity {
	private static final String TAG = "INaturalistPrefsActivity";
	public static final String REAUTHENTICATE_ACTION = "reauthenticate_action";
	private LinearLayout mSignInLayout;
	private LinearLayout mSignOutLayout;
	private TextView mUsernameTextView;
	private TextView mPasswordTextView;
	private TextView mSignOutLabel;
	private Button mSignInButton;
	private Button mSignOutButton;
	private Button mSignUpButton;
	private SharedPreferences mPreferences;
	private SharedPreferences.Editor mPrefEditor;
	private ProgressDialog mProgressDialog;
	private ActivityHelper mHelper;
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	    setContentView(R.layout.preferences);
	    
	    mPreferences = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
	    mPrefEditor = mPreferences.edit();
	    mHelper = new ActivityHelper(this);
	    
	    
	    mSignInLayout = (LinearLayout) findViewById(R.id.signIn);
	    mSignOutLayout = (LinearLayout) findViewById(R.id.signOut);
	    mUsernameTextView = (TextView) findViewById(R.id.username);
	    mPasswordTextView = (TextView) findViewById(R.id.password);
	    mSignOutLabel = (TextView) findViewById(R.id.signOutLabel);
	    mSignInButton = (Button) findViewById(R.id.signInButton);
	    mSignOutButton = (Button) findViewById(R.id.signOutButton);
	    mSignUpButton = (Button) findViewById(R.id.signUpButton);
	    
	    toggle();
	    
        mSignInButton.setOnClickListener(new View.OnClickListener() {
			//@Override
			public void onClick(View v) {
				signIn();
			}
		});
        
        mSignOutButton.setOnClickListener(new View.OnClickListener() {
		//	@Override
			public void onClick(View v) {
				signOut();
			}
		});
        
        mSignUpButton.setOnClickListener(new View.OnClickListener() {
         //   @Override
            public void onClick(View v) {
            	Resources res = getResources();
            	mHelper.confirm(res.getString(R.string.Ready_to_sign_up_res), 
                		res.getString(R.string.You_re_about_to_visit_iNaturalist_org_where_you_can_sign_up_for_a_new_account_res )+"" + 
                		res.getString(R.string.Once_you_ve_confirmed_your_new_account_by_clicking_the_link_in_the_confirmation_res)+"" + 
                		res.getString(R.string.email_you_ll_receive_you_can_come_back_here_to_enter_your_username_and_password_res)+ "", 
                        new DialogInterface.OnClickListener() {
                 //   @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse(INaturalistService.HOST + "/users/new"));
                        startActivity(i);
                    }
                });
            }
        });
        
	    if (getIntent().getAction() != null && getIntent().getAction().equals(REAUTHENTICATE_ACTION)) {
	    	signOut();
	    	Resources res = getResources();
	    	mHelper.alert(res.getString(R.string.Username_or_password_was_invalid_please_sign_in_again_res)+".");
	    }
	}
	
	@Override
	protected void onResume() {
	    super.onResume();
	    mHelper = new ActivityHelper(this);
	}
	
	private void toggle() {
	    String username = mPreferences.getString("username", null);
	    if (username == null) {
	    	mSignInLayout.setVisibility(View.VISIBLE);
	    	mSignOutLayout.setVisibility(View.GONE);
	    } else {
			Resources res = getResources();
	    	mSignInLayout.setVisibility(View.GONE);
	    	mSignOutLayout.setVisibility(View.VISIBLE);
	    	mSignOutLabel.setText(res.getString(R.string.Signed_in_as_res) +""+ username);
	    }
	}
	
	private class SignInTask extends AsyncTask<String, Void, Boolean> {
		private String mUsername;
		private String mPassword;
		private Activity mActivity;
		
		public SignInTask(Activity activity) {
			mActivity = activity;
		}
		
		protected Boolean doInBackground(String... pieces) {
			mUsername = pieces[0];
			mPassword = pieces[1];
	        return INaturalistService.verifyCredentials(mUsername, mPassword);
	    }
		
		protected void onPreExecute() {
			Resources res = getResources();
			mProgressDialog = ProgressDialog.show(mActivity, "", res.getString(R.string.Signing_in_res), true);
		}

	    protected void onPostExecute(Boolean result) {
	    	Resources res = getResources();
	    	if (result) {
				Toast.makeText(mActivity, res.getString(R.string.Signed_in_res), Toast.LENGTH_SHORT).show();
				mProgressDialog.dismiss();
			} else {
				mProgressDialog.dismiss();
				mHelper.alert(res.getString(R.string.Sign_in_failed_res));
				return;
			}
			
			mPrefEditor.putString("username", mUsername);
			String credentials = Base64.encodeToString(
					(mUsername + ":" + mPassword).getBytes(), Base64.URL_SAFE|Base64.NO_WRAP
			);
			mPrefEditor.putString("credentials", credentials);
			mPrefEditor.putString("password", mPassword);
			mPrefEditor.commit();
			toggle();
	    }
	}
	
	private void signIn() {
		String username = mUsernameTextView.getText().toString().trim();
		String password = mPasswordTextView.getText().toString().trim();
		if (username.isEmpty() || password.isEmpty()) {
			Resources res = getResources();
			mHelper.alert(res.getString(R.string.Username_and_password_cannot_be_blank_res));
			return;
		}
		
		new SignInTask(this).execute(username, password);
	}
	
	private void signOut() {
		mPrefEditor.remove("username");
		mPrefEditor.remove("credentials");
		mPrefEditor.remove("password");
		mPrefEditor.commit();
		toggle();
	}
}
