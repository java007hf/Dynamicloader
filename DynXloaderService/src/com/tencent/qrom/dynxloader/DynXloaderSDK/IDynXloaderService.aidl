
package com.tencent.qrom.dynxloader.DynXloaderSDK;



interface IDynXloaderService {
	//
	int addModule(String pkgName, String processName, String modulePath);
	
	//
	int removeModule(String pkgName, String processName);
	
	//
	int checkModuleExist(String pkgName, String processName, String modulePath);
}
