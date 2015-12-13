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
import android.provider.Settings.SettingNotFoundException;

import com.ogp.gpstogglerm.log.ALog;

 
public class GPSActuator extends BroadcastReceiver implements GPSActuatorInterface
{
	private static final String					TAG								= "GPSActuator";
	private static final int					UNKNOWN							= -1;
	
	private static GPSActuatorInterface			singletonGPSActuatorInterface 	= null; 
	private static List<GPSCallbackInterface>	listCallbacks					= new ArrayList<GPSCallbackInterface>();
	
	private Context								context;
	private int 								gpsStatusI						= UNKNOWN;	

	
	
	
	public static GPSActuatorInterface Factory(Context context)
	{
		if (null == singletonGPSActuatorInterface)
		{
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT)
			{
				singletonGPSActuatorInterface = new GPSActuator(context.getApplicationContext());
			}
			else
			{
				singletonGPSActuatorInterface = new GPSActuatorLegacy(context.getApplicationContext());
			}
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

	
	protected GPSActuator(Context context)
	{
		this.context  = context;
	
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
	

	protected void initStatus() 
	{
		try 
		{
			gpsStatusI = Settings.Secure.getInt(context.getContentResolver(),
											    Settings.Secure.LOCATION_MODE);
			
			if (Settings.Secure.LOCATION_MODE_HIGH_ACCURACY == gpsStatusI)
			{
				gpsStatusI = Settings.Secure.LOCATION_MODE_BATTERY_SAVING;
			}
			else
			{
				gpsStatusI = Settings.Secure.LOCATION_MODE_HIGH_ACCURACY;
			}
		} 
		catch (SettingNotFoundException e) 
		{
			gpsStatusI = Settings.Secure.LOCATION_MODE_BATTERY_SAVING;
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
		return UNKNOWN != gpsStatusI;
	}
	
	
	@Override
	public boolean isGPSOn()
	{
		try 
		{
			return Settings.Secure.LOCATION_MODE_HIGH_ACCURACY == Settings.Secure.getInt(context.getContentResolver(),
					   																	 Settings.Secure.LOCATION_MODE);
		} 
		catch (SettingNotFoundException e) 
		{
			return false;
		}
	}

	
	@Override
	public void turnGpsOn() 
	{
		try
		{
			gpsStatusI = Settings.Secure.getInt(context.getContentResolver(),
					  							   Settings.Secure.LOCATION_MODE);

			if (Settings.Secure.LOCATION_MODE_HIGH_ACCURACY == gpsStatusI)
			{
				gpsStatusI = Settings.Secure.LOCATION_MODE_BATTERY_SAVING;
			}
			
			Settings.Secure.putInt (context.getContentResolver(),
									Settings.Secure.LOCATION_MODE,
									Settings.Secure.LOCATION_MODE_HIGH_ACCURACY);	
			
			ALog.i(TAG, "turnGpsOn. New value: " + Settings.Secure.LOCATION_MODE_HIGH_ACCURACY);

			refreshGPSStatus();
		}
		catch(Exception e)
		{
			ALog.e(TAG, "turnGpsOn. !!! Exception !!!");
			e.printStackTrace();
		}

		ALog.v(TAG, "turnGpsOn. Exit.");
	}


	@Override
	public void turnGpsOff() 
	{
		ALog.v(TAG, "turnGpsOff. Entry...");

		try
		{
			if (UNKNOWN == gpsStatusI)
			{
				gpsStatusI = Settings.Secure.LOCATION_MODE_BATTERY_SAVING;

				ALog.i(TAG, "turnGpsOff_6. Old value preserved: " + gpsStatusI);
			}
			else
			{
				Settings.Secure.putInt (context.getContentResolver(),
										Settings.Secure.LOCATION_MODE,
										gpsStatusI);
				ALog.i(TAG, "turnGpsOff. New value: " + gpsStatusI);
			}

			refreshGPSStatus();
		}
		catch(Exception e)
		{
			ALog.e(TAG, "turnGpsOff. !!! Exception !!!");
			e.printStackTrace();
		}

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
