
package com.tencent.qrom.dynxloader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;

public class XloaderReceiver extends BroadcastReceiver{
	Xloader mXloader;
    private Handler mHandler = new Handler(){
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch (msg.what) {
				case 0: 
				{
					Logger.d("TODO: mXloader.xload here");
				}
				default:
					break;
			}
		}

    };
    
	@Override
	public void onReceive(Context context, Intent intent) {
		String socketPath = intent.getStringExtra("socket");  
        String socket = socketPath.substring(12);
        mXloader = new Xloader(socket);
        mHandler.sendEmptyMessageDelayed(0, 1000);
	}

}
