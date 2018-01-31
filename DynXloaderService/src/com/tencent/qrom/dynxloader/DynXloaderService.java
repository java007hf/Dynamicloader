
package com.tencent.qrom.dynxloader;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.app.ActivityManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;

import com.tencent.qrom.dynxloader.DynXloaderSDK.IDynXloaderService;

import dalvik.system.PathClassLoader;

public class DynXloaderService extends Service{
	
	private static DynXloaderServiceImpl mDynXloaderServiceImpl;
	Xloader mXtracer;
	private Context mContext;
	public static final String ACTION_AMS_START_PROCESS = "com.qrom.dynxloader.startprocess";
	
	private static final String PREFERENCE_NAME = "com.qrom.dynxloader.pref";
	private static final String FIRST_BOOT = "firstboot";
	
	private static final String XBRIDGE_DEX_NAME = "bridge.jar"; //XposedBridge.jar
	//private static final String XBRIDGE_SO_NAME = "bridge"; //libxposed.so
	//private static final String CLIENT_SO_NAME = "client"; //inject.so
	
	private static final String XLOADER_SYSTEM_DEX_NAME = "xandroid.jar"; //
	@Override
    public void onCreate() {
		Logger.d("DynXloaderService onCreate");
		
		mContext = getApplicationContext();
		if (mDynXloaderServiceImpl == null) {
			mDynXloaderServiceImpl = new DynXloaderServiceImpl(mContext);
		}
		
		SharedPreferences mSetting = getSharedPreferences(PREFERENCE_NAME,
				Context.MODE_PRIVATE);
		
		//if(mSetting.getBoolean(FIRST_BOOT, true)){
			if(extractBridge() && extractXandroid()) {
				Editor mEditor = mSetting.edit();		
				mEditor.putBoolean(FIRST_BOOT, false);
				mEditor.commit();
			}
		//}
	}
	

	public boolean extractBridge(){
		File cacheDir = getCacheDir();
		if(!cacheDir.exists()){
			cacheDir.mkdir();
		}
		
		boolean ret = AssetsHelper.extraceToLocal(getApplicationContext(), XBRIDGE_DEX_NAME, 
						cacheDir.getAbsolutePath() + File.separator + XBRIDGE_DEX_NAME);
		
		File f = new File(cacheDir.getAbsolutePath() + File.separator + XBRIDGE_DEX_NAME);
		if(f.exists()){
			f.setReadable(true, false);
		}
		
		preloadDex(getCacheDir().getAbsolutePath() + File.separator + XBRIDGE_DEX_NAME);
		return ret;
	}
	
	public boolean extractXandroid(){		
		File cacheDir = getCacheDir();
		if(!cacheDir.exists()){
			cacheDir.mkdir();
		}
		
		boolean ret = AssetsHelper.extraceToLocal(getApplicationContext(), XLOADER_SYSTEM_DEX_NAME, 
				cacheDir.getAbsolutePath() + File.separator + XLOADER_SYSTEM_DEX_NAME);
		
		File f = new File(cacheDir.getAbsolutePath() + File.separator + XLOADER_SYSTEM_DEX_NAME);
		if(f.exists()){
				f.setReadable(true, false);
		}
		
		preloadDex(getCacheDir().getAbsolutePath() + File.separator + XLOADER_SYSTEM_DEX_NAME);
		return ret;
	}
	
	public void preloadDex(String modulePath) {
		Logger.d("preloadDex, modulePath:" + modulePath + ", dexPath:" + getDexPath(modulePath));
		File f = new File(getDexPath(modulePath));
		if(f.exists()){
			Logger.d("Dex exist,delete it first");
			f.delete();
		}
		PathClassLoader dcl = new PathClassLoader(modulePath, this.getClassLoader());
	}

    private static String getDexPath(String apkPath) {
    	String dexPath = apkPath.replace("/", "@").substring(1);
    	return "/data/dalvik-cache/" + dexPath + "@classes.dex";
    }
    
	@Override
	public IBinder onBind(Intent arg0) {
		Logger.d("onBind");
		// TODO Auto-generated method stub
		if (mDynXloaderServiceImpl == null) {
			mDynXloaderServiceImpl = new DynXloaderServiceImpl(mContext);
		}
		Logger.d("onBind mDynXloaderServiceImpl = " + mDynXloaderServiceImpl);
		return mDynXloaderServiceImpl;
	}
	
	@Override
    public void onDestroy() {
		Logger.d("DynXloaderService onDestroy ");
        super.onDestroy();
        if (mDynXloaderServiceImpl != null) {
        	//mDynXloaderServiceImpl.destroyServiceImpl();
        }
    }
    
	public static class ModuleInfo {
		public String pkgName;
		public String processName;
		public String modulePath;
		//boolean loaded;
		
		ModuleInfo ()  {
			pkgName = null;
			processName = null;
			modulePath = null;
			//loaded = false;
		}
		
		ModuleInfo (String pkn, String prn, String mp) {
			pkgName = pkn;
			processName = prn;
			modulePath = mp;
			//loaded = false;
		}
	}
	
	private class DynXloaderServiceImpl extends IDynXloaderService.Stub {
		
		ArrayList<ModuleInfo> mModuleList = new ArrayList<ModuleInfo>();
		ArrayList<ModuleInfo> mPendingModuleList = new ArrayList<ModuleInfo>();
		ModuleInfo mCurAddModule;
		
		DynXloaderServiceImpl(Context context) {
			
			IntentFilter filter = new IntentFilter();
			filter.addAction(ACTION_AMS_START_PROCESS);
			mContext.registerReceiver(mIntentReceiver, filter, null, null);
			
			mXtracer = new Xloader("xloaderd");
			
	        readSharedPreferences();
			
			mHandler.sendEmptyMessageDelayed(XLOADER_SYSTEM_SERVER_STARTPROCESS, 3000);
	       
		}
		
		private static final int CHECK_PROCESS_WHETHER_EXIST = 1;
		private static final int XLOADER_SYSTEM_SERVER_STARTPROCESS = 2;
		private static final int CHECK_PROCESS_HAD_STARTED_BEFORE_ADDMODULE = 3;
		private static final int CHECK_HAD_STARTED_BEFORE_XLOADERSERVICE = 4;
		int mCheckNums = 0;
		private final Handler mHandler = new Handler() {
	        public void handleMessage(Message msg) {
	            switch (msg.what) {
		            case CHECK_PROCESS_WHETHER_EXIST: {
						Logger.d("handleMessage CHECP_PROCESS_WHETHER_EXIST mCheckNums = " + mCheckNums);
						synchronized (mPendingModuleList) {
							Iterator<ModuleInfo> iter = mPendingModuleList.iterator();  
							while(iter.hasNext()){  
								ModuleInfo mi = iter.next();
				            	if (queryAllRunningAppInfo(mi.processName, mContext)) {
				            		sendCmdToXlodaerProcess(mi.pkgName, mi.processName, mi.modulePath);
				            		iter.remove();
				            	}
							}
							if (!mPendingModuleList.isEmpty() && mCheckNums < 5){
								mCheckNums++;
								mHandler.sendEmptyMessageDelayed(CHECK_PROCESS_WHETHER_EXIST, 1000);
							}
		            	}
		                break;
		            }
		            
		            case XLOADER_SYSTEM_SERVER_STARTPROCESS: {
		            	Logger.d("handleMessage XLOADER_SYSTEM_SERVER_STARTPROCESS");
		            	sendCmdToXlodaerProcess("android", "system_server", getCacheDir().getAbsolutePath() + File.separator + XLOADER_SYSTEM_DEX_NAME);
		            	mHandler.sendEmptyMessageDelayed(CHECK_HAD_STARTED_BEFORE_XLOADERSERVICE, 1000);
		            	break;
		            }
		            
		            case CHECK_PROCESS_HAD_STARTED_BEFORE_ADDMODULE: {
		            	if (queryAllRunningAppInfo(mCurAddModule.processName, mContext)) {
		            		sendCmdToXlodaerProcess(mCurAddModule.pkgName, mCurAddModule.processName, mCurAddModule.modulePath);
		            	}
		            	break;
		            }
					
		            case CHECK_HAD_STARTED_BEFORE_XLOADERSERVICE: {
		            	for (ModuleInfo tmpAp : mModuleList) {
			            	if (queryAllRunningAppInfo(tmpAp.processName, mContext)) {
			            		sendCmdToXlodaerProcess(tmpAp.pkgName, tmpAp.processName, tmpAp.modulePath);
			            	}
		            	}
		            	break;
		            }
		        }
	        }
	   };
		private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();
							
				if (action.equals(ACTION_AMS_START_PROCESS)) {
					
					String pkgName = intent.getStringExtra("pkgName");
					String prName = intent.getStringExtra("processName");
					
					Logger.d("receive action = " + action + ", pkgName = " + pkgName + ", processName = " + prName);
					
					for (ModuleInfo tmpAp : mModuleList) {
						Logger.d("check list : pkgName = " + tmpAp.pkgName + ", processName = " + tmpAp.processName);
						if (pkgName.equals(tmpAp.pkgName) && prName.equals(tmpAp.processName)) {
							Logger.d("target package and process found, ready for process start watching");
							synchronized (mPendingModuleList) {
								mPendingModuleList.add(tmpAp);
								mCheckNums = 0;
							}
							mHandler.sendEmptyMessageDelayed(CHECK_PROCESS_WHETHER_EXIST, 100);
							return;
						}
			        }
					Logger.d("target package/process not found, ingore this process");
				}
			}
		};
		
		//
		public int addModule(String pkgName, String processName, String modulePath){
			Logger.d("add Module Request, package:" + pkgName + ", process:" + processName + ", modulePath:" + modulePath);
			if (!verifyCallerPerm()) 
				return -1;
			boolean found = false;
			for (ModuleInfo tmpAp : mModuleList) {
				if (pkgName.equals(tmpAp.pkgName) && processName.equals(tmpAp.processName)) {
					found = true;
					break;
				}
	        }
			
			if (!found) {
				ModuleInfo mi = new ModuleInfo(pkgName, processName, modulePath);
				mModuleList.add(mi);
				
				mCurAddModule = mi;
				mHandler.sendEmptyMessageDelayed(CHECK_PROCESS_HAD_STARTED_BEFORE_ADDMODULE, 1000);
				
				writeSharedPreferences();			
				preloadDex(modulePath);
			} else {
				Logger.d("Module exist, Just Update it, package:" + pkgName + ", process:" + processName + ", modulePath:" + modulePath);
				preloadDex(modulePath);
			}
			
			return 0;
		}
		
		//
		public int removeModule(String pkgName, String processName) {
			if (!verifyCallerPerm()) 
				return -1;
			//ModuleInfo mi = new ModuleInfo(pkgName, processName, modulePath);
			for (ModuleInfo tmpAp : mModuleList) {
				if (pkgName.equals(tmpAp.pkgName) && processName.equals(tmpAp.processName)) {
					mModuleList.remove(tmpAp);
					writeSharedPreferences();
					break;
				}
	        }
			
			return 0;
		}
		
		public int checkModuleExist(String pkgName, String processName, String modulePath) {
			int found = 0;
			for (ModuleInfo tmpAp : mModuleList) {
				if ( pkgName.equals(tmpAp.pkgName) && processName.equals(tmpAp.processName)
						&& modulePath.equals(tmpAp.modulePath) ) {
					found = 1;
					break;
				}
	        }
			
			return found;
		}
		
		void sendCmdToXlodaerProcess(String pkn, String prn, String modulePath) {
			Logger.d("=============================sendCmdToXlodaerProcess pkn = " + pkn + ", prn = " + prn + ", mp = " + modulePath);
			preloadDex(modulePath);
			mXtracer.xload(pkn, prn, modulePath);
		}
		
		
		void  writeSharedPreferences() { 
			
	    	SharedPreferences  user = getSharedPreferences("module_list", MODE_PRIVATE); 
	    	
	    	SharedPreferences.Editor myEditor = user.edit();
	    	
	    	int num = mModuleList.size();
	    	myEditor.putInt("AppNums", num);
	
			Logger.d("writeSharedPreferences------------------AppNums = " + num);
			int index = 1;
			for (ModuleInfo tmpAp : mModuleList) {
				myEditor.putString("pkgName"+index, tmpAp.pkgName);
	            myEditor.putString("processName"+index, tmpAp.processName);
	            myEditor.putString("modulePath"+index, tmpAp.modulePath);
	            //myEditor.putBoolean("loaded"+index, tmpAp.loaded);
	            
	            index++;
	        }
			myEditor.commit(); 
			
			Logger.d("writeSharedPreferences-------the end-----------");
	    } 
		
			   
	    private void  readSharedPreferences() { 
	    	
	    	mModuleList.clear();
	    	
	    	SharedPreferences  user = getSharedPreferences("module_list", 0);
	    	int ge = user.getInt("AppNums", -1);
	    	//intent.get
	    	//Logger.d("CloudBackupService readSharedPreferences ge = " + ge + ", mUserName = " + mUserName);

	    	for(int index = 1; index <= ge; index++) {
	    		ModuleInfo tmpAp = new ModuleInfo();
	    		tmpAp.pkgName =  user.getString("pkgName"+index, null);
	    		tmpAp.processName = user.getString("processName"+index, null);
	        	tmpAp.modulePath = user.getString("modulePath"+index, null);
	        	//tmpAp.loaded = user.getBoolean("loaded"+index, false);
	        	
	        	mModuleList.add(tmpAp);
	    		
	    	}

	    } 
	    	    

		 private boolean queryAllRunningAppInfo(String procName, Context context) {  
	    	 boolean isRunning = false;
	    	 
	    	 if (procName == null || context == null) {
	    		 Logger.d("queryAllRunningAppInfo pkg == null || context == null");
	    		 return false;
	    	 }
	    	 
	    	 ActivityManager am = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
	    	 
	    	  List<ActivityManager.RunningServiceInfo> services = am.getRunningServices(100);
	    	  
			  int NS = services != null ? services.size() : 0;
			  for (int i=0; i<NS; i++) {
			      ActivityManager.RunningServiceInfo si = services.get(i);
			     
			      // We are not interested in services that have not been started
			      // and don't have a known client, because
			      // there is nothing the user can do about them.
			      if (!si.started && si.clientLabel == 0) {
			          services.remove(i);
			          i--;
			          NS--;
			          continue;
			      }
			      // We likewise don't care about services running in a
			      // persistent process like the system or phone.
			      if ((si.flags&ActivityManager.RunningServiceInfo.FLAG_PERSISTENT_PROCESS)
			              != 0) {
			          services.remove(i);
			          i--;
			          NS--;
			          continue;
			      }

			      if (procName.equals(si.process)) {
			    	  Logger.d("queryAllRunningAppInfo si = " + si.process
					          + ", uid=" + si.uid + ", pid=" + si.pid);
			    	  isRunning = true;
			 
			      }
			  }

			  // Retrieve list of running processes, organizing them into a sparse
			  // array for easy retrieval.
			  List<ActivityManager.RunningAppProcessInfo> processes
			          = am.getRunningAppProcesses();
			  final int NP = (processes != null ? processes.size() : 0);
			  
			 // mTmpAppProcesses.clear();
			  for (int i=0; i<NP; i++) {
			      ActivityManager.RunningAppProcessInfo pi = processes.get(i);
			      // 
			     // Log.d(TAG, "RunningAppProcessInfo pi = " + pi.processName
				  //        + ", uid=" + pi.uid + ", pid=" + pi.pid+ ", pi.flags=" + pi.flags);
			      // mTmpAppProcesses.put(pi.pid, new AppProcessInfo(pi));
			      if (procName.equals(pi.processName)) {
			    	  isRunning = true;
			      }
			  }
	  
			  return isRunning;
	     }  
		 
		 private boolean verifyCallerPerm()  {
			 int ret = -1;
			 Logger.d("verifyCallerPerm() ");
		 	//String caller = getPackageSig();
		 	int uid = Binder.getCallingUid();
		 	String[] packages = getPackageManager().getPackagesForUid(uid);
		 	getSignature(packages[0]);
		 	
		 	//getSignature(
		 	ret = compSignatures(packages[0], "com.android.systemui"); //platform
		 	if (ret == 0) {
		 		return true;
		 	} else {
		 		return false;
		 	}
		 }
		 
		 public String getSignature(String pkgName) {
			   PackageInfo packageInfo;
			   Signature[] signatures;
			   PackageManager manager;
			   StringBuilder builder = new StringBuilder();
			   String sig = null;
			   
			   Logger.d("getSignature pkgName = " + pkgName);
			   
	           try {
	        	   manager = getPackageManager();

	               packageInfo = manager.getPackageInfo(pkgName, PackageManager.GET_SIGNATURES);

	               signatures = packageInfo.signatures;

	               for (Signature signature : signatures) {
	                   builder.append(signature.toCharsString());
	               }
	               sig = builder.toString();
	          
	           } catch (NameNotFoundException e) {
	               e.printStackTrace();
	           }
	           Logger.d("getSignature sig = " + sig);
	           return sig;
		 }
		 
		 //0----match
		 int compSignatures(String pk1, String pk2) {
			 
			 int ret = getPackageManager().checkSignatures(pk1, pk2);
			 Logger.d("compSignatures pk1 = " + pk1 + ", ret = " + ret);
			 return ret;
		 }
		 

	} // end of CloudBackupImpl
	

	
	
}
