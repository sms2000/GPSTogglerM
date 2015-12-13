package com.ogp.selfsystemizer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;

import com.ogp.selfsystemizer.KernelServices.Mount;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources.NotFoundException;
import android.os.PowerManager;


public class Systemizer 
{
	private static final String[] SystemSpaceWhitelist 		= {"/system/priv-app/", "/system/app/"};
	private static final String[] CacheDirectoryWhiteList   = {"/data/dalvik-cache/profiles/", "/data/dalvik-cache/%s/"};
	private static final String   UserSpace					= "/data/app/";
	@SuppressLint("SdCardPath")
	private static final String   DataSpace					= "/data/data/";
	private static final String   SystemLibSpace			= "/system/lib/";
	private static final String   SYSTEM_FS					= "/system";
	private static final String   BASE_APK					= "base.apk";
	private static final String   EXT_APK					= ".apk";
	
	private SystemizerCallback 	systemizerCallback;
	private Context 			context;
	private String				apkPath;
	private boolean 			isSystemized;
	private int 				systemDirIndex = -1;
	
	
	public Systemizer(SystemizerCallback systemizerCallback) throws Exception
	{
		this.systemizerCallback	= systemizerCallback;
		this.context 		    = systemizerCallback.getContext().getApplicationContext();
		
		if (!recognizeSelf()) 
		{
			throw new Exception("Failed to recognize self.");
		}
	}
	

	public String getApkPath()
	{
		return apkPath;
	}
	
	
	public String getApkName() 
	{
		String[] parts = apkPath.split("/");
		if (parts.length < 2)
		{
			return apkPath;
		}
		else if (parts[parts.length - 1].equals(BASE_APK))
		{
			return parts[parts.length - 2].split("-")[0] + EXT_APK;
		}
		else
		{
			String oldName = parts[parts.length - 1].split("-")[0];
			
			if (!oldName.endsWith(EXT_APK))
			{
				oldName += EXT_APK;
			}
					
			return  oldName;
		}
	}


	public String getApplicationFriendlyName()
	{
		try
		{
			int stringId = context.getApplicationInfo().labelRes;
			return context.getString(stringId);
		}
		catch(NotFoundException e)
		{
			return getApkName();			
		}
	}
	
	
	public void executeOnRoot(String command) 
	{
		putToLog("Attempting to obtain 'root'!");
		
		try
		{
	    	Process 		 chperm 	= Runtime.getRuntime().exec ("su");
			DataOutputStream os 		= new DataOutputStream(chperm.getOutputStream());
			DataInputStream  is 		= new DataInputStream(chperm.getInputStream());

			putToLog("'Root' has been granted!");
	    	Thread.sleep(50);	

	    	applyCommand (os, is, getLibPath() + "/" + command);
	    	
	    	closeSu(chperm, os);

	    	putToLog("executeOnRoot finished.");
		}
		catch(Exception e)
		{
			e.printStackTrace();
			putToLog("Error: no 'root' could be obtained.");
		}
	}

	
	public String getRecommendedSystemPath() 
	{
		return SystemSpaceWhitelist[systemDirIndex];
	}


	public String getPackageName()
	{
		return context.getApplicationContext().getPackageName();
	}
	
	
	public boolean isSystemized()
	{
		return isSystemized;
	}

	
	public boolean doMakeSystem()
	{
		putToLog("Attempting to obtain 'root'!");
		
		try
		{
	    	Process 		 chperm 	= Runtime.getRuntime().exec ("su");
			DataOutputStream os 		= new DataOutputStream(chperm.getOutputStream());
			DataInputStream  is 		= new DataInputStream(chperm.getInputStream());

			putToLog("'Root' has been granted!");
	    	Thread.sleep(50);	

	    	if (!mountSystemWriteable(os, is))
	    	{
				putToLog("Error: failed to mount /system as writable.");
				closeSu(chperm, os);
				return false;
	    	}


			putToLog("Copying APK to /system/<>...");
			
			String appPath = getRecommendedSystemPath() + "/" + getApplicationFriendlyName() + EXT_APK;
			
	    	if (!copyFiles(os, is, getApkPath(), appPath))
		    {
				closeSu(chperm, os);
				return false;
		    }			 
		    

			if (!applyPermissions (os, is, appPath, false))
			{
				closeSu(chperm, os);
				return false;
		    }			 
		    
			
			putToLog("Create the 'lib' directory.");
			
			String libDir = SystemLibSpace + "/" + getApplicationFriendlyName();
			if (!createDirectory (os, is, libDir))
			{
				closeSu(chperm, os);
	    		return false;
			}

			
			if (!applyPermissions (os, is, libDir, true))
			{
				closeSu(chperm, os);
	    		return false;
			}
			
			putToLog("Copying native modules.");

			if (!copyFiles(os, is, getLibPath() + "/*", libDir + "/"))
			{
				closeSu(chperm, os);
	    		return false;
			}
			
			
			if (!applyPermissions (os, is, libDir + "/*", true))
			{
				closeSu(chperm, os);
	    		return false;
			}

		    
			putToLog("Uninstall original APK and reboot...");
			Thread.sleep(500);
			
			String command 	= String.format("pm uninstall -k %s\nreboot 1", getPackageName()); 

			if (!applyCommand (os, is, command))
		    {
				putToLog("Error: systemizing failed.");
				closeSu(chperm, os);
	    		return false;
		    }			 
	    	
	    	closeSu(chperm, os);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			putToLog("Error: no 'root' could be obtained.");
			return false;
		}
		
		putToLog("Finished. Please reboot.");
		return true;
	}


	public boolean doMakeUser()
	{
		putToLog("Attempting to obtain 'root'!");
		
		try
		{
	    	Process 		 chperm 	= Runtime.getRuntime().exec ("su");
			DataOutputStream os 		= new DataOutputStream(chperm.getOutputStream());
			DataInputStream  is 		= new DataInputStream(chperm.getInputStream());

			putToLog("'Root' has been granted!");
	    	Thread.sleep(50);	

	    	if (!mountSystemWriteable(os, is))
	    	{
				putToLog("Error: failed to mount /system as writable.");
				closeSu(chperm, os);
				return false;
	    	}

	    	
	    	String apkDir = String.format("%s/%s-1", 
	    							      UserSpace, getPackageName()); 

		    if (!createDirectory(os, is, apkDir))
		    {
				closeSu(chperm, os);
				return false;
		    }			 
	    	
		    
		    if (!applyPermissions (os, is, apkDir, true))
		    {
				closeSu(chperm, os);
				return false;
		    }			 

		    
		    if (!applySystemOwner(os, is, apkDir))
		    {
				closeSu(chperm, os);
				return false;
		    }			 

		    
		    if (!copyFiles(os, is, getApkPath(), apkDir + "/" + BASE_APK))
		    {
				closeSu(chperm, os);
				return false;
		    }			 
	    	
	    	
		    if (!applyPermissions (os, is, apkDir + "/" + BASE_APK, false))
		    {
				closeSu(chperm, os);
				return false;
		    }			 

		    
		    if (!applySystemOwner(os, is, apkDir + "/" + BASE_APK))
		    {
				closeSu(chperm, os);
				return false;
		    }			 


		    String targetDir = String.format("%s/%s-1/lib/%s/", UserSpace, getPackageName(), getArchitectureLib());
		    if (!createDirectory (os, is, targetDir))
		    {
				closeSu(chperm, os);
				return false;
		    }


		    if (!applyPermissions (os, is, targetDir, true))
		    {
				closeSu(chperm, os);
				return false;
		    }			 
		    

		    if (!applySystemOwner(os, is, targetDir))
		    {
				closeSu(chperm, os);
				return false;
		    }			 
		    
		    
		    if (!copyFiles (os, is, getLibPath() + "/*", targetDir + "/"))
		    {
				closeSu(chperm, os);
				return false;
		    }			 
		    
		    
	    	if (!applyPermissions(os, is, targetDir + "/*", true))
	    	{
	    		closeSu(chperm, os);
	    		return false;
	    	}			 

		    
		    if (!applySystemOwner(os, is, targetDir + "/*"))
		    {
				closeSu(chperm, os);
				return false;
		    }			 
		    
		    if (!removeOneFile (os, is, getApkPath()))
		    {
				closeSu(chperm, os);
				return false;
		    }			 
		    
			
		    String dirFormat = "*" + getPackageName() + "*";
	    	
	    	for (String dir : CacheDirectoryWhiteList)
	    	{
		    	if (!removeFilesInDirectoryRecursively (os, is, String.format(dir, getArchitectureLib()), dirFormat))
		    	{
					closeSu(chperm, os);
					return false;
		    	}

		    	Thread.sleep(50);	
	    	}


		    if (!removeWholeDirectory(os, is, SystemLibSpace + "/" + getApplicationFriendlyName()))
		    {
				closeSu(chperm, os);
				return false;
		    }			 
	    		    	
	    	
	    	closeSu(chperm, os);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			putToLog("Error: no 'root' could be obtained.");
			return false;
		}
		
		putToLog("Finished. Please reboot.");
		return true;
	}


	public boolean doUninstall()
	{
		try
		{
	    	Process 		 chperm 	= Runtime.getRuntime().exec ("su");
			DataOutputStream os 		= new DataOutputStream(chperm.getOutputStream());
			DataInputStream  is 		= new DataInputStream(chperm.getInputStream());
			String 			 command 	= String.format("pm uninstall %s", getPackageName()); 
			
			putToLog("'Root' has been granted!");
			Thread.sleep(50);	

			if (isSystemized())
			{
				if (!mountSystemWriteable (os, is))
				{
					closeSu(chperm, os);
		    		return false;
				}
				

				if (!removeOneFile(os, is, getApkPath()))
				{
					closeSu(chperm, os);
		    		return false;
				}
					
					
				command += "\nreboot 1";
				
			}
			
		    if (!applyCommand (os, is, command))
		    {
				putToLog("Error: uninstall failed [2].");
				closeSu(chperm, os);
	    		return false;
		    }			 

		    putToLog("Uninstall succeeded. Close the program finally.");
			
	    	closeSu(chperm, os);
			return true;
		}
		catch(Exception e)
		{
			e.printStackTrace();
			putToLog("Error: no 'root' could be obtained.");
			return false;
		}
	}
	
	
	public void doReboot()
	{
		putToLog("Attempting to obtain 'root'!");
		
		// System way to reboot
		try
		{
			PowerManager pm = (PowerManager)systemizerCallback.getContext().getSystemService(Context.POWER_SERVICE);
			pm.reboot(null);
			
			Thread.sleep(1000);
		}
		catch(Exception e)
		{
		}
		
		
		// Root way to reboot
		try
		{
	    	Process 		 chperm 	= Runtime.getRuntime().exec ("su");
			DataOutputStream os 		= new DataOutputStream(chperm.getOutputStream());
			DataInputStream  is 		= new DataInputStream(chperm.getInputStream());

			putToLog("'Root' has been granted!");
	    	Thread.sleep(50);	

			putToLog("Rebooting...");
	    	Thread.sleep(250);	
			
		    boolean res = applyCommand (os, is, "reboot 1");
	    	Thread.sleep(250);	
		    if (!res)
		    {
				putToLog("Error: failed to reboot.");
				closeSu(chperm, os);
				return;
		    }			 
		}
		catch(Exception e)
		{
			e.printStackTrace();
			putToLog("Error: no 'root' could be obtained.");
		}
	}

	
	private boolean removeFilesInDirectoryRecursively (DataOutputStream os, DataInputStream is, String directory, String format)
	{
		putToLog(String.format("Removing directory recursively [%s] for [%s].", 
							   directory, format));

    	String command = String.format ("rm -rf %s/%s", 
	    									directory, format);

	    boolean res = applyCommand (os, is, command);
	    if (res)
	    {
			putToLog(String.format("The files [%s] from the directory [%s] have been removed recursively.",
									format, directory));
	    }
	    else
	    {
			putToLog(String.format("Failed to remove the files [%s] from the directory [%s].",
									format, directory));
	    }			 
		    
	    return res; 
	}
	
	
	private boolean removeWholeDirectory (DataOutputStream os, DataInputStream is, String directory)
	{
		putToLog("Removing directory: " + directory);
		
		try
		{
	    	String command = String.format ("rm -rf %s", 
	    									directory);

		    boolean res = applyCommand (os, is, command);
		    if (res)
		    {
				putToLog(String.format("The whole directory [%s] has been removed recursively.",
										directory));
		    }
		    else
		    {
				putToLog(String.format("Failed to remove the directory [%s].",
										directory));
		    }			 
		    
		    return res; 
		}
		catch(Exception e)
		{
		}
		
		return false;
	}

	
	private boolean removeOneFile (DataOutputStream os, DataInputStream is, String file)
	{
		putToLog("Removing file: " + file);

		try
		{
	    	String command = String.format ("rm -f %s", 
	    									file);

		    boolean res = applyCommand (os, is, command);
		    if (res)
		    {
				putToLog(String.format("The file [%s] has been removed recursively.",
										file));
		    }
		    else
		    {
				putToLog(String.format("Failed to remove the file [%s].",
										file));
		    }			 
		    
		    return res; 
		}
		catch(Exception e)
		{
		}
		
		return false;
	}

	
	private boolean recognizeSelf() 
	{
		PackageManager 	pm = context.getPackageManager();
		String         	pn = context.getPackageName();
		boolean			ready = false;
		
		
		// Decide about /system directory.
		for (systemDirIndex = 0; systemDirIndex < SystemSpaceWhitelist.length; systemDirIndex++)
		{
			File file = new File(SystemSpaceWhitelist[systemDirIndex]);
			if (file.exists() && file.isDirectory())
			{
				break;
			}
		}
		
		
		// Find the package info for this APK
		for (ApplicationInfo app : pm.getInstalledApplications(0)) 
		{
			if (pn.equals(app.packageName))
			{
				//Log.d("PackageList", "package: " + app.packageName + ", sourceDir: " + app.sourceDir);

				apkPath = app.sourceDir;
				
				for (String path : SystemSpaceWhitelist)
				{
					if (apkPath.startsWith(path))
					{
						isSystemized = true;
						ready 		 = true;
						break;
					}
				}
				
				if (!ready)
				{
					if (apkPath.startsWith(UserSpace))
					{
						isSystemized = false;
						ready 		 = true;
					}
				}
				
				if (ready)
				{
					break;
				}
			}
		}
		
		return ready;
	}


	private boolean analyzeOutput(DataInputStream is)
	{
		try 
		{
			boolean inputReady = false;
			
			for (int i = 0; i < 10; i++)
			{
				Thread.sleep(50);
				
				if (is.available() > 0)
				{
					inputReady = true;
					break;
				}
				
			}
			
			if (inputReady)
			{
				@SuppressWarnings("deprecation")
				String returned = is.readLine();
				
				putToLog(">>>> " + returned);
			}
			return true;
		} 
		catch (Exception e) 
		{
			putToLog("Exception in analyzeOutput.");
		}
		
		return false; 
	}
	
	
	private boolean applyCommand (DataOutputStream os, DataInputStream is, String command)
	{
		try
		{
			os.writeBytes (command + "\n");
		    os.flush();
		    
		    if (!analyzeOutput (is))
		    {
		    	return false;
		    }
		    return true;  				
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
		return false;
	}


	private boolean mountSystemWriteable (DataOutputStream os, DataInputStream is) 
	{
		putToLog("Mounting the /system partition as writable.");
		
		Mount sysFS = new KernelServices().findFS (SYSTEM_FS);

    	String command = String.format ("mount -o rw,remount -t %s %s",
    							 		sysFS.mType, 
    							 		SYSTEM_FS);

	    boolean res = applyCommand (os, is, command);
	    if (res)
	    {
			putToLog("The remount succeeded.");
			return true;
	    }
	    else
	    {
			putToLog("The remount failed.");
			return false;
	    }			    
	}

	
	private boolean applyPermissions (DataOutputStream os, DataInputStream is, String path, boolean executeAlso)
	{
		putToLog("Setting permissions.");

		String command = String.format ("chmod %s %s",
    									executeAlso ? "755" : "644",
				 						path);

    	boolean res = applyCommand (os, is, command);
    	if (res)
    	{
    		putToLog("The permissions have been applied successfully.");
    	}
    	else
    	{
    		putToLog("Error: failed to apply proper permissions.");
    	}

    	return res;
	}			 

	
	private boolean applySystemOwner(DataOutputStream os, DataInputStream is, String path)
	{
		putToLog("Setting system owner.");

		String command = String.format ("chown system:system %s", 
				 						path);

		boolean res = applyCommand (os, is, command);
		if (res)
		{
			putToLog("The system owner has been applied successfully.");
		}
		else
		{
			putToLog("Error: failed to apply system owner.");
		}
		
		return res;
	}

	
	private boolean createDirectory (DataOutputStream os, DataInputStream is, String path)
	{
		putToLog("Creating directory: " + path);

		String command = String.format ("mkdir -p %s",
				 						path);

    	boolean res = applyCommand (os, is, command);
    	if (res)
    	{
    		putToLog("The directory has been created successfully.");
    	}
    	else
    	{
    		putToLog("Error: failed to create the directory.");
    	}

    	return res;
	}			 


	private boolean copyFiles(DataOutputStream os, DataInputStream is, String source, String target)
	{
		putToLog(String.format("Copy from [%s] to [%s].", source, target));
		
	    String command = String.format ("cp %s %s", 
				 					    source, 
				 					    target);

	    boolean res = applyCommand (os, is, command);
	    if (res)
	    {
	    	putToLog("Copy succeeded.");
	    }
	    else
	    {
	    	putToLog("Error: copy failed.");
	    }			 
		
	    return res;
	}
	
	private void closeSu(Process chperm, DataOutputStream os)
	{
		try
		{
		    os.writeBytes ("exit\n");
		    os.flush();

		    chperm.waitFor();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	
	private void putToLog(String str)
	{
		try
		{
			systemizerCallback.putToLog (str);
		}
		catch(Throwable th)
		{
		}
	}

	
	private String getLibPath()
	{
		return context.getApplicationInfo().nativeLibraryDir; 
	}
	
	
	private String getArchitectureLib() throws Exception
	{
		String arch = System.getProperty ("os.arch");

		if (arch.startsWith("aarch64"))
		{
			return "arm64";
		}
		else if (arch.startsWith("arm"))
		{
			return "arm";	
		}
						
		throw new Exception("Cannot determinate the CPU Architecture.");
	}
}
