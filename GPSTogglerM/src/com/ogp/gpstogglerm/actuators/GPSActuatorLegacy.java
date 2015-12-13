package com.ogp.gpstogglerm.actuators;

import java.util.ArrayList;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.Handler;
import android.provider.Settings;
import android.provider.Settings.Secure;

import com.ogp.gpstogglerm.log.ALog;

 
public class GPSActuatorLegacy extends BroadcastReceiver implements GPSActuatorInterface
{
	private static final String					TAG								= "GPSActuatorLegacy";
	
	private static GPSActuatorInterface			singletonGPSActuatorInterface 	= null; 
	private static List<GPSCallbackInterface>	listCallbacks					= new ArrayList<GPSCallbackInterface>();

	private Context								context;
	private String 								gpsStatusS						= null;	

	
	public static GPSActuatorInterface Factory(Context context)
	{
		if (null == singletonGPSActuatorInterface)
		{
			singletonGPSActuatorInterface = new GPSActuatorLegacy(context.getApplicationContext());
		}
		
		return singletonGPSActuatorInterface;
	}

	
	@Override
	public void registerReceiver(GPSCallbackInterface gpsCallbackInterface) 
	{
		if (!listCallbacks.contains(gpsCallbackInterface))
		{
			listCallbacks.add (gpsCallbackInterface);
		}
	}


	@Override
	public void unregisterReceiver(GPSCallbackInterface gpsCallbackInterface) 
	{
		if (listCallbacks.contains(gpsCallbackInterface))
		{
			listCallbacks.remove(gpsCallbackInterface);
		}
	}

	
	protected GPSActuatorLegacy(Context context) 
	{
		this.context 	= context;

		context.getApplicationContext().registerReceiver(this, 
				 									     new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));

		new Handler().post (new Runnable()
		{
			@Override
			public void run() 
			{
				initStatus();
			}
		}); 
	}

	
	@SuppressWarnings("deprecation")
	protected void initStatus() 
	{
		gpsStatusS = Settings.Secure.getString (context.getContentResolver(),
				   								Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
		
		if (gpsStatusS.contains("gps"))
		{
			gpsStatusS.replace ("gps", "");
			gpsStatusS.replace (",,", ",");
		}
		else
		{
			gpsStatusS += ",gps";
		}
	}


	@Override
	public void onReceive (Context 	context, 
						   Intent 	intent) 
	{
		ALog.v(TAG, "Entry...");

		String action = intent.getAction(); 

		if (action.equals (LocationManager.PROVIDERS_CHANGED_ACTION))
		{
			refreshGPSStatus();
				
    		ALog.d(TAG, "GPS status refreshed.");
		}
	}


	@Override
	public boolean isReady()
	{
		return null != gpsStatusS;
	}
	
	
	@Override
	public boolean isGPSOn()
	{
		String currentSet = Secure.getString(context.getContentResolver(), LocationManager.GPS_PROVIDER);
		
		return currentSet.contains ("gps");
	}

	
	@SuppressWarnings("deprecation")
	@Override
	public void turnGpsOn() 
	{
		ALog.v(TAG, "turnGpsOn. Entry...");

		String newSet = String.format ("%s,%s",
									   gpsStatusS,
									   LocationManager.GPS_PROVIDER);

		try
		{
			Settings.Secure.putString (context.getContentResolver(),
									   Settings.Secure.LOCATION_PROVIDERS_ALLOWED,
									   newSet);	
			ALog.i(TAG, "turnGpsOn. New string: " + newSet);
		}
		catch(Exception e)
		{
			ALog.e(TAG, "turnGpsOn. !!! Exception !!!");
			e.printStackTrace();
		}

		refreshGPSStatus();

		ALog.v(TAG, "turnGpsOn. Exit.");
	}


	@SuppressWarnings("deprecation")
	@Override
	public void turnGpsOff() 
	{
		ALog.v(TAG, "turnGpsOff. Entry...");

		try
		{
			Settings.Secure.putString (context.getContentResolver(),
									   Settings.Secure.LOCATION_PROVIDERS_ALLOWED,
									   gpsStatusS);
			ALog.i(TAG, "turnGpsOff. New string: " + gpsStatusS);
		}
		catch(Exception e)
		{
			ALog.e(TAG, "turnGpsOff. !!! Exception !!!");
			e.printStackTrace();
		}

		refreshGPSStatus();

		ALog.v(TAG, "turnGpsOff. Exit.");
	}

	
	private void refreshGPSStatus() 
	{
		try
		{
			for (GPSCallbackInterface callback : listCallbacks)
			{
				try
				{
					callback.gpsStatusChanged(context);
				}
				catch(Exception e)
				{
					listCallbacks.remove (callback);
				}
			}

			ALog.d(TAG, "refreshGPSStatus. Succeeded.");
		}
		catch(Exception e)
		{
			e.printStackTrace();
			ALog.e(TAG, "refreshGPSStatus. !!! EXCEPTION !!!");
		}
	}
}
