package com.ogp.gpstogglerm;

import com.ogp.gpstogglerm.log.ALog;

import android.app.Activity;


public class OnActivity extends Activity 
{
	private static final String TAG 			= "OnActivity";
	

	@Override
	protected void onResume()
	{
		ALog.v(TAG, "Entry...");

		StateMachine.init (this);

		super.onResume();

		GPSTogglerService.setGPSStateManually (true);

		finish();
		
		ALog.v(TAG, "Exit.");
	}
}
