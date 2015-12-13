package com.ogp.gpstogglerm;

import android.os.Bundle;
import android.os.Handler;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import com.ogp.selfsystemizer.Systemizer;
import com.ogp.selfsystemizer.SystemizerCallback;

import com.ogp.gpstogglerm.actuators.GPSActuator;
import com.ogp.gpstogglerm.actuators.GPSActuatorInterface;
import com.ogp.gpstogglerm.actuators.GPSCallbackInterface;
import com.ogp.gpstogglerm.log.ALog;
import com.ogp.gpstogglerm.xml.VersionXMLParser;


public class GPSTogglerActivity extends Activity implements GPSCallbackInterface, SystemizerCallback
{
	private static final String 		TAG 					= "GPSTogglerActivity";

	private Button						btSwapGps;
	private CheckBox					watchWaze;
	private CheckBox					turnBT;
	private CheckBox					useNotification;
	private CheckBox					splitAware;
	private Systemizer 					systemizer 				= null;
	private GPSActuatorInterface		gpsActuator 			= null;
	private boolean 					systemizing				= false;
	

	@SuppressLint("InflateParams")
	@Override
	protected void onCreate (Bundle savedInstanceState) 
	{
		ALog.v(TAG, "onCreate. Entry...");

		super.onCreate (savedInstanceState);
		
		try 
		{
			systemizer = new Systemizer(this);
		} 
		catch (Exception e) 
		{
			showErrorDialog(R.string.fatal_error_systemizer);
			return;
		} 
		
		ViewGroup viewGroup = (ViewGroup)getLayoutInflater().inflate (R.layout.activity_main, 
																	  null);
		setContentView (viewGroup);

		btSwapGps		= (Button)viewGroup.findViewById   (R.id.button);
		watchWaze		= (CheckBox)viewGroup.findViewById (R.id.waze);
		turnBT			= (CheckBox)viewGroup.findViewById (R.id.bt);
		useNotification	= (CheckBox)viewGroup.findViewById (R.id.notification);
		splitAware		= (CheckBox)viewGroup.findViewById (R.id.splitAware);
				
		StateMachine.init (this);
		VersionXMLParser.printVersion (this);

		TextView verText = (TextView)viewGroup.findViewById (R.id.version);
		verText.setText (String.format (getResources().getString (R.string.verison_format), 
										StateMachine.getVersion()));

		watchWaze.		setChecked (StateMachine.getWatchGPSSoftware());
		turnBT.	  		setChecked (StateMachine.getTurnBT());
		useNotification.setChecked (StateMachine.getUseNotification());
		splitAware.		setChecked (StateMachine.getSplitAware());
		
		ALog.v(TAG, "onCreate. Exit.");
	}


	@Override
	protected void onResume() 
	{
		super.onResume();

		if (!systemizer.isSystemized() && !systemizing)
		{
			new Handler().postDelayed(new Runnable()
			{
				@Override
				public void run() 
				{
					showSystemizeDialog();
				}
			}, 500);
			
			return;  
		}
		
		gpsActuator = GPSActuator.Factory(this);
		gpsActuator.registerReceiver (this);

		initAferSystem();

        if (StateMachine.getRebootRequired())
        {
// Maybe this time user wants to reboot?
			showRebootDialog();
        }
	}
	
	
	@Override
	protected void onPause() 
	{
		if (null != gpsActuator)
		{
			gpsActuator.unregisterReceiver (this);
		}

		super.onPause();
	}

	
	private void initAferSystem() 
	{
		requestCurrentGPSState();
		
		GPSTogglerService.startServiceManually (this);
	}


	@Override
	public boolean onCreateOptionsMenu (Menu menu) 
	{
		getMenuInflater().inflate (R.menu.activity_main, menu);
		return true;
	}
	
	
	public void requestCurrentGPSState()
	{
		ALog.d(TAG, "requestCurrentGPSState. GPS now " + (gpsActuator.isGPSOn() ? "on" : "off"));

			
		String strStatus = getResources().getString (R.string.gps_status);
		strStatus += " " + getResources().getString (gpsActuator.isGPSOn() ? R.string.gps_on : R.string.gps_off);
		
		btSwapGps.setText (strStatus);
	}
	
	
	public void clickButton (View _)
	{
		ALog.v(TAG, "clickButton. Entry...");
		
		ALog.w(TAG, gpsActuator.isGPSOn() ? "clickButton. Pressed when ON" : "clickButton. Pressed when OFF");

		if (gpsActuator.isGPSOn())
		{
			gpsActuator.turnGpsOff();
		}
		else
		{
			gpsActuator.turnGpsOn();
		}
		
		ALog.v(TAG, "clickButton. Exit.");
	}
	
	
	public void clickUninstall (View _)
	{
		ALog.v(TAG, "clickUninstall. Entry...");
		
		showUninstallDialog();
		
		ALog.v(TAG, "clickUninstall. Exit.");
	}

	
	public void clickSelectPackets (View view)
	{
		ALog.v(TAG, "clickSelectPackets. Entry...");
		
		Intent intent = new Intent(this, SelectActivity.class);
		startActivity (intent);

		ALog.v(TAG, "clickSelectPackets. Exit.");
	}
	
	
	public void clickNotification (View view)
	{
		ALog.v(TAG, "clickNotification. Entry...");

		StateMachine.setUseNotification (((CheckBox)view).isChecked());
		StateMachine.writeToPersistantStorage();

		GPSTogglerService.setServiceForeground();
		
		ALog.v(TAG, "clickNotification. Exit.");
	}
	
	
	public void clickSplitAware (View view)
	{
		ALog.v(TAG, "clickSplitAware. Entry...");

		StateMachine.setSplitAware (((CheckBox)view).isChecked());
		StateMachine.writeToPersistantStorage();

		ALog.v(TAG, "clickSplitAware. Exit.");
	}

	
	public void clickWatchWaze (View view)
	{
		ALog.v(TAG, "clickWatchWaze. Entry...");

		StateMachine.setWatchGPSSoftware (((CheckBox)view).isChecked());
		StateMachine.writeToPersistantStorage();
		
		GPSTogglerService.updateWidgets (getApplicationContext());
		
		ALog.v(TAG, "clickWatchWaze. Exit.");
	}

	
	public void clickTurnBT (View view)
	{
		ALog.v(TAG, "clickTurnBT. Entry...");

		StateMachine.setTurnBT (((CheckBox)view).isChecked());
		StateMachine.writeToPersistantStorage();

		GPSTogglerService.updateBTAsGPS();
		
		ALog.v(TAG, "clickTurnBT. Exit.");
	}
	
		
	public void clickDebugging (View view)
	{
		ALog.v(TAG, "clickDebugging. Entry...");

		StateMachine.setUseDebugging (((CheckBox)view).isChecked());
		StateMachine.writeToPersistantStorage();

		ALog.v(TAG, "clickDebugging. Exit.");
	}
	

	private void showSystemizeDialog() 
	{
		AlertDialog.Builder dialog = new AlertDialog.Builder(this);
		dialog.setTitle (R.string.warning);
		dialog.setMessage (R.string.warning_systemize);
		dialog.setPositiveButton (R.string.ok, 
								  new DialogInterface.OnClickListener() 
		{
			public void onClick (DialogInterface 	dialog, 
								 int 				id) 
			{
				systemizing = true;

				dialog.cancel();
				doSystemize();
			}
		});

		
		dialog.setNegativeButton (R.string.cancel, 
								  new DialogInterface.OnClickListener() 
		{
			public void onClick (DialogInterface 	dialog, 
								 int 				id) 
			{
				dialog.cancel();
				finish();
			}
		});

		dialog.show();
	}

	

	private void doSystemize() 
	{
		if (systemizer.doMakeSystem())
		{
			showRebootDialog();	
		}
		else
		{
			showErrorDialog(R.string.error_systemizer_failed);
		}
	}


	private void showRebootDialog() 
	{
		AlertDialog.Builder dialog = new AlertDialog.Builder(this);
		dialog.setTitle (R.string.warning);
		dialog.setMessage (R.string.warning_reboot_required);
		dialog.setPositiveButton (R.string.ok, 
								  new DialogInterface.OnClickListener() 
		{
			public void onClick (DialogInterface 	dialog, 
								 int 				id) 
			{
				dialog.cancel();
				systemizer.doReboot();
			}
		});

		dialog.setNegativeButton (R.string.cancel, 
								  new DialogInterface.OnClickListener() 
		{
			public void onClick (DialogInterface 	dialog, 
								 int 				id) 
			{
				dialog.cancel();
			}
		});

		dialog.show();
	}
	
	
	private void showUninstallDialog() 
	{
		AlertDialog.Builder dialog = new AlertDialog.Builder(this);
		dialog.setTitle (R.string.warning);
		dialog.setMessage (systemizer.isSystemized() ? R.string.warning_uninstall_reboot : R.string.warning_uninstall);
		dialog.setPositiveButton (R.string.ok, 
								  new DialogInterface.OnClickListener() 
		{
			public void onClick (DialogInterface 	dialog, 
								 int 				id) 
			{
				dialog.cancel();
				systemizer.doUninstall();
			}
		});

		dialog.setNegativeButton (R.string.cancel, 
								  new DialogInterface.OnClickListener() 
		{
			public void onClick (DialogInterface 	dialog, 
								 int 				id) 
			{
				dialog.cancel();
			}
		});

		dialog.show();
	}

	
	private void showErrorDialog(int error) 
	{
		AlertDialog.Builder dialog = new AlertDialog.Builder(this);
		dialog.setTitle (R.string.error);
		dialog.setMessage (error);
		dialog.setPositiveButton (R.string.yes, 
								  new DialogInterface.OnClickListener() 
		{
			public void onClick (DialogInterface 	dialog, 
								 int 				id) 
			{
				dialog.cancel();
				finish();
			}
		});


		dialog.show();
	}

	
	public static void startMainActivity (Context context) 
	{
		ALog.v(TAG, "Entry...");

		Intent intent = new Intent(context.getApplicationContext(), 
				   				   GPSTogglerActivity.class);

		intent.setFlags (Intent.FLAG_ACTIVITY_NEW_TASK);

		context.getApplicationContext().startActivity (intent);

		ALog.v(TAG, "Exit.");
	}


	@Override
	public void gpsStatusChanged(Context context) 
	{
		requestCurrentGPSState();
	}


	@Override
	public void putToLog(String str) 
	{
		ALog.w(TAG, "Systemizer: " + str);
	}


	@Override
	public Context getContext() 
	{
		return this;
	}
}
