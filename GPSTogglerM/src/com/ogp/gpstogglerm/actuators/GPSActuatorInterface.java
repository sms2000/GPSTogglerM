package com.ogp.gpstogglerm.actuators;


public interface GPSActuatorInterface 
{
	public boolean 	isReady				();
	public boolean 	isGPSOn				();
	public void 	turnGpsOn			();
	public void 	turnGpsOff			();

	public void 	registerReceiver   	(GPSCallbackInterface gpsCallbackInterface); 
	public void 	unregisterReceiver 	(GPSCallbackInterface gpsCallbackInterface); 
}
