package de.robv.android.xposed;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

import android.app.ActivityThread;
import android.app.Application;

public class ApplicationHelper {	
	@SuppressWarnings("unchecked")
	public static ArrayList<Application> getAllApplications(){
		ArrayList<Application> list = null;
	    try {
		    Class<?> activityThreadClass =
		            Class.forName("android.app.ActivityThread");
		    Field fieldAllApps = activityThreadClass.getDeclaredField("mAllApplications");
		    fieldAllApps.setAccessible(true);
		    list = (ArrayList<Application>)fieldAllApps.get(getCurrentActivityThread());
		} catch (Exception e) {
			e.printStackTrace();
		}
	    return list;
	}
	
	public static Application getCurrentApplication(){
		Application app = null;
		try {
		    final Class<?> activityThreadClass =
		            Class.forName("android.app.ActivityThread");
		    final Method method = activityThreadClass.getMethod("currentApplication");
		    app = (Application) method.invoke(null, (Object[]) null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return app;
	}
	
	public static ActivityThread getCurrentActivityThread(){
		ActivityThread at = null;
		try {
		    final Class<?> activityThreadClass =
		            Class.forName("android.app.ActivityThread");
		    final Method method = activityThreadClass.getMethod("currentActivityThread");
		    at = (ActivityThread) method.invoke(null, (Object[]) null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return at;
	}
}
