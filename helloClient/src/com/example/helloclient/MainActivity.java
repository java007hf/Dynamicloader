package com.example.helloclient;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.tencent.helloworld.R;
import com.tencent.qrom.dynxloader.DynXloaderSDK.DynXloaderServiceProxy;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


public class MainActivity extends Activity{
	private static final String TAG = "helloClient";
	private static final String XLOADER_MODULE = "helloModule.jar";
	private static final String TARGET_PACKAGE = "com.kingroot.master";
	private static final String TARGET_PROCESS = "com.kingroot.master:service";
	private TextView mTextViewTips;
	private Button mButtonExtract;
	private Button mButtonAdd;
	private Button mButtonRm;
	private DynXloaderServiceProxy mProxy;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        mTextViewTips = (TextView)findViewById(R.id.text_tips);
        
        mButtonExtract = (Button)findViewById(R.id.button_extract);
        mButtonExtract.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String modulePath = getCacheDir().getAbsolutePath() + File.separator + XLOADER_MODULE;
				File f = new File(modulePath);
				if(f.exists()){
					Log.d(TAG, f.getName() + "exist, delete the exist one before extract.");
					f.delete();
				}
				boolean ret = extractAssetsToLocal(getApplicationContext(), XLOADER_MODULE, modulePath);
				if(ret)
					mTextViewTips.setText("extract module success \n [" + modulePath + "]");
				else
					mTextViewTips.setText("extract module failed");
			}
        });
        
        mButtonAdd = (Button)findViewById(R.id.button_add);
        mButtonAdd.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				//getDynXloaderServiceProxy().addModule("android", "system_server", "/data/app/com.tencent.xposed.hello-1.apk");
				int ret = getDynXloaderServiceProxy().addModule(TARGET_PACKAGE, TARGET_PROCESS, getCacheDir().getAbsolutePath() + File.separator + XLOADER_MODULE);
				if(ret == 0) {
					mTextViewTips.setText("addModule success : \n target package : " + 
							TARGET_PACKAGE + "\n target process : " + TARGET_PROCESS + "\n module : " + XLOADER_MODULE);
				} else if(ret == 2) {
					mTextViewTips.setText("service busy, retry");
				} else {
					mTextViewTips.setText("addModule failed");
				}
			}
        });
              
        mButtonRm = (Button)findViewById(R.id.button_rm);
        mButtonRm.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				int ret = getDynXloaderServiceProxy().removeModule(TARGET_PACKAGE, TARGET_PROCESS);
				if(ret == 0)
					mTextViewTips.setText("removeModule success");
				else if(ret == 2)
					mTextViewTips.setText("service busy, retry");
				else
					mTextViewTips.setText("removeModule failed");
			}
        });
    }
    
    @Override
	protected void onDestroy() {
    	if (mProxy == null) {
    		mProxy.release();
    	}
		super.onDestroy();
	}
    
    synchronized DynXloaderServiceProxy getDynXloaderServiceProxy(){
			if (mProxy == null) {
				mProxy = new DynXloaderServiceProxy(0, this);
			}
			return mProxy;
    }
    
	public boolean extractAssetsToLocal(Context context, String srcName, String outPath) {
		boolean ret = false;
        InputStream is = null;  
        OutputStream os = null;
		try {
			is = context.getAssets().open(srcName);
			os = new FileOutputStream(outPath);
	        byte[] buffer = new byte[1024];  
	        int len;
	        while((len = is.read(buffer)) != -1)
	        {
	            os.write(buffer, 0, len); 
	        }
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
	        if(os != null) {
	        	try {
	        		os.flush();
	        		os.close();
	        		File f = new File(outPath);
	    			if(f.exists())
	    				f.setReadable(true, false);
	        		ret = true;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	        }
	        if(is != null) {
	        	try {
					is.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}  
	        }
		}
		return ret;
    }
}
