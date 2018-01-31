package com.tencent.qrom.dynxloader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;

public class BootBroadcastReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context arg0, Intent arg1) {
		// TODO Auto-generated method stub
	    if(arg1.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
		    
		    Logger.d("BootBroadcastReceiver onReceive int = " + arg1);
		    
            Intent bootActivityIntent = new Intent(arg0, DynXloaderService.class);
            
            //bootActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            //arg0.startService(bootActivityIntent);
            arg0.startServiceAsUser(bootActivityIntent, UserHandle.CURRENT_OR_SELF);
            
        }
	}

}
