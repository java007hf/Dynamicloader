package com.example.hellomodule;

public class KingRootUtils {
	//http://www.cppblog.com/guojingjia2006/archive/2013/02/18/197911.html
	
	public static String [] PERMISSIONS = {
		"android.permission.SEND_SMS",
		"android.permission.CALL_PHONE" + "&" + "android.permission.CALL_PRIVILEGED",
		"android.permission.INSTALL_PACKAGES",
		"android.permission.CHANGE_NETWORK_STATE",
		"android.permission.CHANGE_WIFI_STATE",
		"android.permission.BLUETOOTH" + "&" + "android.permission.BLUETOOTH_ADMIN",
		"android.permission.NFC",
		"android.permission.READ_SMS" + "&" + "android.permission.WRITE_SMS",
		"android.permission.RECEIVE_MMS",
		"android.permission.READ_CONTACTS" + "&" + "android.permission.WRITE_CONTACTS",
		"android.permission.READ_CALL_LOG",
		"android.permission.CAMERA",
		"android.permission.RECORD_AUDIO" + "&" + "android.permission.READ_PHONE_STATE",
		"android.permission.RECORD_AUDIO",
		"android.permission.ACCESS_FINE_LOCATION" + "&" + "android.permission.ACCESS_COARSE_LOCATION",
		"android.permission.READ_PHONE_STATE",
	};
	
	public static String[] TIP = {
		"发送短信",
		"拨打电话",
		"读取已安装应用列表",
		"打开移动网络",
		"打开wifi网络",
		"打开蓝牙",
		"打开NFC",
		"读取短信记录",
		"读取彩信记录",
		"读取通讯录内容",
		"获取通话记录",
		"使用摄像头",
		"开启通话录音功能",
		"开启录音功能",
		"获取当前位置信息",
		"读取手机设备信息",
	};
	
	public static String getPermission(String conect) {
		for (int i=0;i<TIP.length;i++) {
			if (conect.contains(TIP[i])) {
				return PERMISSIONS[i];
			}
		}
		
		return conect;
	}
}
