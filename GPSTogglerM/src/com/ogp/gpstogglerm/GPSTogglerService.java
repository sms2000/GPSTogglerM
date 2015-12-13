package com.ogp.gpstogglerm;

import com.ogp.gpstogglerm.actuators.GPSActuator;
import com.ogp.gpstogglerm.actuators.GPSActuatorInterface;
import com.ogp.gpstogglerm.actuators.GPSCallbackInterface;
import com.ogp.gpstogglerm.log.ALog;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.content.BroadcastReceiver;


public class GPSTogglerService extends Service implements GPSCallbackInterface
{
	private static final String 		TAG 					= "GPSTogglerService";
	
	public static final String			GPS 					= "gps";

	private static final long 			DOUBLE_CLICK_DELAY 		= 300;			// 300 ms
	private static final long 			SCREEN_OFF_DELAY		= 5000;			// 5 seconds		

	public static final String 			WIDGET_CLICK 			= "Widget.Click";
	
	private static GPSTogglerService	thisService				= null;
	private Handler						messageHandler			= new Handler();
	private ActivityManagement 			activityManagement;
	private WatchdogThread 				watchdogThread;
	private boolean						gpsSoftwareStatus		= false;
	private long 						firstClickTime			= 0;
	private GPSActuatorInterface		gpsActuator 			= null;

	
	private final BroadcastReceiver widgetClickReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent) 
		{
			processClickOverWidget(context.getApplicationContext());
		}
	};
	
	
	private class ProcessSingleClick implements Runnable
	{
		private Context applicationContext;
		
		
		public ProcessSingleClick(Context applicationContext) 
		{
			this.applicationContext = applicationContext;
		}

		
		@Override
		public void run() 
		{
			processSingleClick (applicationContext);
		}
	}
	
	
	private class ScreenStatusChanged implements Runnable
	{
		private boolean		status;
		
		
		private ScreenStatusChanged(boolean status)
		{
			this.status = status;
		}


		@Override
		public void run() 
		{
			try
			{
				reportScreenStatusInternal (status);
				
				ALog.d(TAG, "Succeeded.");
			}
			catch(Exception e)
			{
				ALog.e(TAG, "EXC(1)");
			}
		}
	}
	
	
																	
@Override
	public IBinder onBind (Intent arg) 
	{
		return null;
	}

	
	@Override
	public void onCreate()
	{
		ALog.v(TAG, "Entry...");

		super.onCreate();

		thisService = this;
		gpsActuator = GPSActuator.Factory(this);
		gpsActuator.registerReceiver (this);
		StateMachine.init (this);

		activityManagement = new ActivityManagement();
		
		IntentFilter intentFilter1 = new IntentFilter(GPSTogglerService.WIDGET_CLICK);
		registerReceiver (widgetClickReceiver, intentFilter1);

		IntentFilter intentFilter2 = new IntentFilter(Intent.ACTION_SCREEN_ON);
		intentFilter2.addAction (Intent.ACTION_SCREEN_OFF);

		registerReceiver (activityManagement, intentFilter2);		
		
		ALog.w(TAG, "Registered screen receivers.");
		
		initWatchdogThread (true);
		
		setItForeground();
		
		ALog.v(TAG, "Exit.");
	}

	
	@Override
	public int onStartCommand (Intent 	intent, 
							   int 		flags, 
							   int 		startId)
	{
		int result = super.onStartCommand (intent, 
										   flags, 
										   startId);

		if (START_NOT_STICKY == result)
		{
			result = START_STICKY;
		}
		
		
		return result;
	}
	

	@Override
	public void onDestroy()
	{
    	ALog.v(TAG, "Entry...");

    	initWatchdogThread (false);
    	gpsSoftwareStatus = false;
    	
		gpsActuator.unregisterReceiver (this);
    	
		unregisterReceiver (activityManagement);
		unregisterReceiver (widgetClickReceiver);

		ALog.w(TAG, "Unregistered screen receivers.");

    	thisService 	= null;
    	
    	super.onDestroy();

    	ALog.v(TAG, "Exit.");
	}


	@SuppressWarnings("deprecation")
	@Override
	public void onStart (Intent 	intent,
						 int 		startId)
	{
    	ALog.v(TAG, "Entry...");

    	super.onStart (intent, 
				       startId);

		thisService = this;

    	ALog.v(TAG, "Exit.");
	}
	
	
	public static void setServiceForeground()
	{
		try
		{
			thisService.setItForeground();
		}
		catch(Exception e)
		{
		}
	}
	
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@SuppressWarnings("deprecation")
	private void setItForeground()
	{
		if (StateMachine.getUseNotification())
		{
			Intent intent = new Intent(this, GPSTogglerActivity.class).setFlags (Intent.FLAG_ACTIVITY_CLEAR_TOP 	| 
						 	 													 Intent.FLAG_ACTIVITY_SINGLE_TOP 	| 
						 	 													 Intent.FLAG_ACTIVITY_NEW_TASK);
			
			PendingIntent pi = PendingIntent.getActivity (this, 0, intent, 0);

			Notification.Builder noteBuilder = new Notification.Builder(this)
													 .setContentTitle(getResources().getString (R.string.app_name))
													 .setContentText(getResources().getString (R.string.notify_active))
													 .setSmallIcon(getResIdByStatus())
													 .setContentIntent(pi);

			
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP)
			{
				noteBuilder.setColor(getResources().getColor (getColorIdByStatus()));

			}
			
			startForeground (1, 
				  	 		 noteBuilder.getNotification());
				
			ALog.d(TAG, "setItForeground. Bringing the service foreground...");
		}
		else
		{
			try
			{
				stopForeground (true);
		    	ALog.d(TAG, "setItForeground. Bringing the service background...");
			}
			catch(Exception e)
			{
			}
		}
	}

	
	private int getResIdByStatus() 
	{
		if (null == gpsActuator || !gpsActuator.isReady())
		{
			return R.drawable.gps_s_unknown;
		}
		else
		{
			if (StateMachine.getWatchGPSSoftware())
			{
				return gpsActuator.isGPSOn() ? R.drawable.gps_s_control_on : R.drawable.gps_s_control_off;
			}
			else
			{
				return gpsActuator.isGPSOn() ? R.drawable.gps_s_on : R.drawable.gps_s_off;
			}
		}
	}
	
	
	private int getColorIdByStatus() 
	{
		if (null == gpsActuator || !gpsActuator.isReady())
		{
			return R.color.gps_unknown;
		}
		else
		{
			if (StateMachine.getWatchGPSSoftware())
			{
				return gpsActuator.isGPSOn() ? R.color.gps_control_on : R.color.gps_control_off;
			}
			else
			{
				return gpsActuator.isGPSOn() ? R.color.gps_on : R.color.gps_off;
			}
		}
	}

	
	public static void startServiceManually (Context 	context) 
	{
		ALog.v(TAG, "Entry...");

		try
		{
			thisService.toString();
		}
		catch(Exception e)
		{
			Intent serviceIntent = new Intent(context.getApplicationContext(), 
					  GPSTogglerService.class);

			context.startService (serviceIntent);
		}
		
		ALog.v(TAG, "Exit.");
	}
	

	public static void reportScreenStatus (boolean 	status) 
	{
		ALog.v(TAG, "Entry...");

		try
		{
			thisService.messageHandler.removeCallbacksAndMessages (null);
			
			thisService.messageHandler.postDelayed (thisService.new ScreenStatusChanged (status), 
													status ? 0 : SCREEN_OFF_DELAY);

			ALog.d(TAG, "Post message succeeded.");
		}
		catch(Exception e)
		{
			ALog.e(TAG, "EXC(1)");
		}
		
		ALog.v(TAG, "Exit.");
	}

	
	public void reportGPSSoftwareStatus (boolean gpsSoftwareRunning) 
	{
		if (gpsSoftwareStatus != gpsSoftwareRunning)
		{
			gpsSoftwareStatus = gpsSoftwareRunning;
			activateGPSForSoftware();
		}
	}

	
	public static void updateBTAsGPS() 
	{
		ALog.v(TAG, "Entry...");

		if (null != thisService)
		{
			thisService.updateBTAsGPSInternal();
		}

		ALog.v(TAG, "Exit.");
	}

	
	public static void updateWidgets (Context applicationContext) 
	{
		ALog.v(TAG, "Entry...");

		if (null != thisService)
		{
			thisService.updateWidget();
		}

		ALog.v(TAG, "Exit.");
	}

	
	public static void setGPSStateManually (boolean turnOn)
	{
		try
		{
			thisService.setGPSStateManuallyInternal (turnOn);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			ALog.e(TAG, "setGPSStateManually. !!! EXCEPTION !!!");
		}
	}
	
	
	private void setGPSStateManuallyInternal (boolean turnOn)
	{
		if (turnOn)
		{
			gpsActuator.turnGpsOn();
		}
		else
		{
			gpsActuator.turnGpsOff();
		}
	}

	
	public static boolean getGPSState()
	{
		try
		{
			return thisService.getGPSStateInternal();
		}
		catch(Exception e)
		{
			e.printStackTrace();
			ALog.e(TAG, "getGPSStateManually. !!! EXCEPTION !!!");
			return false;
		}
	}
	
	
	private boolean getGPSStateInternal()
	{
		return gpsActuator.isGPSOn();
	}

	
	private void swapGPSStateInternal() 
	{
		if (gpsActuator.isGPSOn())
		{
			gpsActuator.turnGpsOff();
		}
		else
		{
			gpsActuator.turnGpsOn();
		}
	}


	private void processClickOverWidget (Context applicationContext) 
	{
		ALog.v(TAG, "Entry...");

		if (0 == firstClickTime)
		{
			firstClickTime = System.currentTimeMillis();
			ALog.d(TAG, "First click registered at " + firstClickTime);
			
			messageHandler.postDelayed (new ProcessSingleClick(applicationContext),
										DOUBLE_CLICK_DELAY);
		}
		else 
		{
			firstClickTime = 0;
			ALog.d(TAG, "Second click registered at " + System.currentTimeMillis());

// Activate activity
			GPSTogglerActivity.startMainActivity (applicationContext);
			
		}
		
		ALog.v(TAG, "Exit.");
	}
	
	
	private void processSingleClick (Context applicationContext)
	{
		ALog.v(TAG, "Entry...");
		
		if (0 == firstClickTime)
		{
			ALog.d(TAG, "Bypass. Do nothing.");
		}
		else
		{
			firstClickTime = 0;
			
			if (!StateMachine.getWatchGPSSoftware())
			{
				swapGPSStateInternal();
			}

			ALog.d(TAG, "Single click activated.");
		}
		
		ALog.v(TAG, "Exit.");
	}

	
	private void reportScreenStatusInternal (boolean 	status) 
	{
		ALog.v(TAG, "Entry...");

		initWatchdogThread (status);
		
		ALog.v(TAG, "Exit.");
	}
	
		
	private void initWatchdogThread (boolean status) 
	{
		ALog.v(TAG, "Entry...");

		if (status)
		{
			if (null == watchdogThread)
			{
				watchdogThread = new WatchdogThread(this);

				ALog.d(TAG, "Watchdog thread started.");
			}
		}
		else 
		{
			if (null != watchdogThread)
			{
				watchdogThread.finish();
				watchdogThread = null;

				ALog.d(TAG, "Watchdog thread finished.");
			}
		}

		ALog.v(TAG, "Exit.");
	}


	private void updateWidget() 
	{
		ALog.v(TAG, "Entry...");

		GPSWidget.createWidgetView (this);

        ALog.v(TAG, "Exit.");
	}


	private void activateGPSForSoftware() 
	{
		ALog.v(TAG, "Entry...");
		
		if (StateMachine.getWatchGPSSoftware())
		{
			setGPSStateManuallyInternal (gpsSoftwareStatus);
				
			ALog.i(TAG, "Attempt to " + (gpsSoftwareStatus ? "activate GPS." : "deactivate GPS."));
		}
		
		ALog.v(TAG, "Exit.");
	}

	
	private void updateBTAsGPSInternal() 
	{
		ALog.v(TAG, "Entry...");
		
		if (StateMachine.getTurnBT())
		{
			BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();

			if (gpsActuator.isGPSOn())
			{
				if (null != btAdapter 
					&& 
					!btAdapter.isEnabled())
				{
					btAdapter.enable();
				}

				ALog.i(TAG, "BT enabled.");
			}
			else
			{
				if (null != btAdapter 
					&& 
					btAdapter.isEnabled())
				{
					btAdapter.disable();
				}
				
				ALog.i(TAG, "BT disabled.");
			}
		}
		
		ALog.v(TAG, "Exit.");
	}


	@Override
	public void gpsStatusChanged(Context context) 
	{
		stopForeground (true);
		setItForeground();
	}
}
