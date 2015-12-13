package com.ogp.gpstogglerm;


import com.ogp.gpstogglerm.actuators.GPSActuator;
import com.ogp.gpstogglerm.actuators.GPSActuatorInterface;
import com.ogp.gpstogglerm.actuators.GPSCallbackInterface;
import com.ogp.gpstogglerm.log.ALog;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.widget.RemoteViews;


public class GPSWidget extends AppWidgetProvider implements GPSCallbackInterface 
{
    private static final String TAG = "GPSWidget";

	private static GPSActuatorInterface	 gpsActuator = null;
    
    
    @Override
    public void onReceive (Context 		context, 
    					   Intent 		intent) 
    {
    	String action = intent.getAction();
    	if (null == action)
    	{
    		action = "<unknown>";
    	}
    	
    	
    	ALog.v(TAG, "Entry for action: " + action);
    	
    	if (action.equals (AppWidgetManager.ACTION_APPWIDGET_ENABLED)
    		 ||
    		action.equals (AppWidgetManager.ACTION_APPWIDGET_UPDATE))
    	{
    		GPSTogglerService.startServiceManually (context);

   			gpsActuator = GPSActuator.Factory(context);
   			gpsActuator.registerReceiver (this);

    		if (action.equals (AppWidgetManager.ACTION_APPWIDGET_UPDATE))
    		{
            	ALog.i(TAG, "Update widgets initiated.");

            	super.onReceive(context, intent);
    		}
    	}
    	else if (action.equals (AppWidgetManager.ACTION_APPWIDGET_DISABLED))
    	{
        	ALog.i(TAG, "Removed receiver.");
        	
   			gpsActuator = GPSActuator.Factory(context);
   			gpsActuator.unregisterReceiver (this);
    	}
        
        ALog.v(TAG, "Exit.");
    }
    
    
	@Override
    public void onUpdate (Context 			context, 
    					  AppWidgetManager 	appWidgetManager,
    					  int[] 			appWidgetIds) 
    {
		ALog.v(TAG, "Entry...");
    	
		GPSTogglerService.startServiceManually (context);
    	
		GPSWidget.createWidgetView (context);
        
        ALog.v(TAG, "Exit.");
    }


	@Override
	public void gpsStatusChanged(Context context) 
	{
		createWidgetView (context);
	}

	
	@SuppressWarnings("deprecation")
	public static void createWidgetView (Context context) 
	{
    	ALog.v(TAG, "Entry...");

    	RemoteViews updateViews = new RemoteViews(context.getPackageName(), 
				  								  R.layout.widget_layout);
    	
    	Drawable drawable = context.getResources().getDrawable (getResIdByStatus());
        Bitmap 	 bitmap	  = ((BitmapDrawable)drawable).getBitmap();

    	updateViews.setImageViewBitmap (R.id.bitmap, 
    								    bitmap);

        Intent intent = new Intent(GPSTogglerService.WIDGET_CLICK);
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast (context, 
        														  0,
        														  intent, 
        														  0);

        updateViews.setOnClickPendingIntent (R.id.widget,
        									 pendingIntent);
        
        ALog.w(TAG, "setOnClickPendingIntent invoked.");
    	
    	
        ComponentName thisWidget = new ComponentName(context, 
				 									 GPSWidget.class);

        AppWidgetManager manager = AppWidgetManager.getInstance (context);
        manager.updateAppWidget (thisWidget, 
        						 updateViews);

        GPSTogglerService.setServiceForeground();
        
        ALog.w(TAG, "updateAppWidget invoked.");
        ALog.v(TAG, "Exit.");
	}


	public static void updateAllWidgets(Context context) 
	{
		Intent intent = new Intent(context.getApplicationContext(), GPSWidget.class);
		intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);

		int[] ids = {0};
		intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
		context.sendBroadcast (intent);
		
		ALog.i(TAG, "updateAllWidgets. Broadcast has been sent.");
	}

	
	private static int getResIdByStatus() 
	{
		if (null == gpsActuator || !gpsActuator.isReady())
		{
			return R.drawable.gps_unknown;
		}
		else
		{
			if (StateMachine.getWatchGPSSoftware())
			{
				return gpsActuator.isGPSOn() ? R.drawable.gps_control_on : R.drawable.gps_control_off;
			}
			else
			{
				return gpsActuator.isGPSOn() ? R.drawable.gps_on : R.drawable.gps_off;
			}
		}
	}
}
