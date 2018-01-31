/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: E:\\workspace\\DynXloaderService\\src\\com\\tencent\\qrom\\dynxloader\\DynXloaderSDK\\IDynXloaderService.aidl
 */
package com.tencent.qrom.dynxloader.DynXloaderSDK;
public interface IDynXloaderService extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements com.tencent.qrom.dynxloader.DynXloaderSDK.IDynXloaderService
{
private static final java.lang.String DESCRIPTOR = "com.tencent.qrom.dynxloader.DynXloaderSDK.IDynXloaderService";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an com.tencent.qrom.dynxloader.DynXloaderSDK.IDynXloaderService interface,
 * generating a proxy if needed.
 */
public static com.tencent.qrom.dynxloader.DynXloaderSDK.IDynXloaderService asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof com.tencent.qrom.dynxloader.DynXloaderSDK.IDynXloaderService))) {
return ((com.tencent.qrom.dynxloader.DynXloaderSDK.IDynXloaderService)iin);
}
return new com.tencent.qrom.dynxloader.DynXloaderSDK.IDynXloaderService.Stub.Proxy(obj);
}
@Override public android.os.IBinder asBinder()
{
return this;
}
@Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
{
switch (code)
{
case INTERFACE_TRANSACTION:
{
reply.writeString(DESCRIPTOR);
return true;
}
case TRANSACTION_addModule:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
java.lang.String _arg1;
_arg1 = data.readString();
java.lang.String _arg2;
_arg2 = data.readString();
int _result = this.addModule(_arg0, _arg1, _arg2);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_removeModule:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
java.lang.String _arg1;
_arg1 = data.readString();
int _result = this.removeModule(_arg0, _arg1);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements com.tencent.qrom.dynxloader.DynXloaderSDK.IDynXloaderService
{
private android.os.IBinder mRemote;
Proxy(android.os.IBinder remote)
{
mRemote = remote;
}
@Override public android.os.IBinder asBinder()
{
return mRemote;
}
public java.lang.String getInterfaceDescriptor()
{
return DESCRIPTOR;
}
//

@Override public int addModule(java.lang.String pkgName, java.lang.String processName, java.lang.String modulePath) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(pkgName);
_data.writeString(processName);
_data.writeString(modulePath);
mRemote.transact(Stub.TRANSACTION_addModule, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
//

@Override public int removeModule(java.lang.String pkgName, java.lang.String processName) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(pkgName);
_data.writeString(processName);
mRemote.transact(Stub.TRANSACTION_removeModule, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
}
static final int TRANSACTION_addModule = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_removeModule = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
}
//

public int addModule(java.lang.String pkgName, java.lang.String processName, java.lang.String modulePath) throws android.os.RemoteException;
//

public int removeModule(java.lang.String pkgName, java.lang.String processName) throws android.os.RemoteException;
}
