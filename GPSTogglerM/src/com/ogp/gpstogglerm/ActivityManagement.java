package com.ogp.gpstogglerm;

import com.ogp.gpstogglerm.log.ALog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;


public class ActivityManagement extends BroadcastReceiver
{
	private static final String TAG 			= "ActivityManagement";


	@Override
	public void onReceive (Context 		context, 
						   Intent 		intent) 
	{
		String action = intent.getAction();
		
		if (null == action)
		{
			ALog.e(TAG, "No action available.");
			return;
		}
		

		if (action.equals (Intent.ACTION_BOOT_COMPLETED))
		{
			StateMachine.init (context);

			if (StateMachine.getRebootRequired())
			{
				StateMachine.setRebootRequired (false);
				StateMachine.writeToPersistantStorage();
			}
			
			GPSTogglerService.startServiceManually (context);
		}
		else if (action.equals (Intent.ACTION_USER_PRESENT))
		{
			GPSTogglerService.startServiceManually (context);
		}
		else if (action.equals (Intent.ACTION_MY_PACKAGE_REPLACED))
		{
			StateMachine.init (context);

			if (StateMachine.getRebootRequired())
			{
				StateMachine.setRebootRequired (false);
				StateMachine.writeToPersistantStorage();
			}
			
			GPSTogglerService.startServiceManually (context);
			
			GPSWidget.updateAllWidgets(context);
		}
		else if (action.equals (Intent.ACTION_SCREEN_OFF))
		{
			GPSTogglerService.reportScreenStatus (false);
		}
		else if (action.equals (Intent.ACTION_SCREEN_ON))
		{
			GPSTogglerService.reportScreenStatus (true);
		}
		else
		{
			ALog.v(TAG, "Caught something: " + action);
		}
		
		
		ALog.w(TAG, "Action: " + intent.getAction());
	}
}
