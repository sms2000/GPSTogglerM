package com.ogp.gpstogglerm;

import com.ogp.gpstogglerm.log.ALog;

import android.app.Activity;


public class OffActivity extends Activity 
{
	private static final String TAG 			= "OffActivity";
	

	@Override
	protected void onResume()
	{
		ALog.v(TAG, "Entry...");

		StateMachine.init (this);

		super.onResume();

		GPSTogglerService.setGPSStateManually (false);

		finish();

		ALog.v(TAG, "Exit.");
	}
}
