package com.ogp.selfsystemizer;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.List;


public class KernelServices 
{
	private static final String 	MOUNTING_POINTS 		= "/proc/mounts";
	
	private static List<Mount>		mountPoints				= new ArrayList<Mount>(); 
	
	
	public class Mount 
	{
	    public String		mDevice;
	    public String		mMountPoint;
	    public String		mType;
	    
	    public Mount(String		device, 
	    			 String		path,
	    			 String 	type) 
	    {
	        mDevice 	= device;
	        mMountPoint = path;
	        mType		= type;
	    }
	}


	public KernelServices()
	{
		if (mountPoints.isEmpty())
		{
			try
			{
				LineNumberReader 	lnr = new LineNumberReader(new FileReader(MOUNTING_POINTS));
				String 				line;
				
				while ((line = lnr.readLine()) != null)
				{
					String[] fields = line.split (" ");
					mountPoints.add (new Mount(fields[0], 		// device
										  	   fields[1],		// mounting point
										  	   fields[2])); 	// type
				}
				
				lnr.close();
			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
		}
	}
	
	
	public Mount findFS (String mountPoint) 
	{
		for (Mount iterator : mountPoints)
		{
			String path = iterator.mMountPoint;  
			
			if (path.equals (mountPoint))
			{
				return iterator;
			}
		}
		
		return null;
	}
}
