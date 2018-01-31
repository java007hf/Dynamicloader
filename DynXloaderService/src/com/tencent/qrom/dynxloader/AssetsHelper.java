package com.tencent.qrom.dynxloader;


import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.content.Context;

public class AssetsHelper {
	static boolean extraceToLocal(Context context, String srcName, String outPath) {
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
