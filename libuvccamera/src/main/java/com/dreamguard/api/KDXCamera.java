package com.dreamguard.api;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.util.Log;
import android.view.Surface;
import android.widget.Toast;

import com.dreamguard.api.R;
import com.dreamguard.usb.camera.CameraHandler;
import com.dreamguard.usb.detect.DeviceFilter;
import com.dreamguard.usb.detect.USBMonitor;
import com.dreamguard.usb.detect.USBStatus;


import java.util.List;

/**
 * Created by hailin on 2016/12/10.
 */

public class KDXCamera {

    private final static String TAG = "KDXCamera";
    /**
     * for accessing USB
     */
    private USBMonitor mUSBMonitor;
    /**
     * Handler to execute camera releated methods sequentially on private thread
     */
    private CameraHandler mHandler;

    private Context context;

    private SurfaceTexture mSurfaceTexture;

    private USBStatus usbStatus = USBStatus.DETACHED;

    private final Object mSync = new Object();

    public void init(Context context) {
        Log.v(TAG, "init :");
        this.context = context;
        mUSBMonitor = new USBMonitor(context, mOnDeviceConnectListener);
        mHandler = CameraHandler.createHandler(context);
        mUSBMonitor.register();

    }

    public void destroy() {
        Log.v(TAG, "destroy :");
        mUSBMonitor.unregister();
        mUSBMonitor.destroy();
        mHandler = null;
    }

    public void setPreviewSize(int width, int height) {
        CameraHandler.PREVIEW_WIDTH = width;
        CameraHandler.PREVIEW_HEIGHT = height;
        CameraHandler.CAPTURE_WIDTH = width;
        CameraHandler.CAPTURE_HEIGHT = height;
        CameraHandler.RECORD_WIDTH = width;
        CameraHandler.RECORD_HEIGHT = height;



    }

    public void setCameraType(CameraType cameraType){
        if(cameraType == CameraType.C3D_NORMAL){
            CameraHandler.is3D = false;
        }else {
            CameraHandler.is3D = true;
        }
    }

    public void setPreviewTexture(SurfaceTexture surfaceTexture){
        mSurfaceTexture = surfaceTexture;
    }

    public void startPreview(){

    }

    public boolean open(int id) {
        Log.v(TAG, "open :");
        final List<DeviceFilter> filter = DeviceFilter.getDeviceFilters(context, R.xml.device_filter);
        List<UsbDevice> deviceList = mUSBMonitor.getDeviceList(filter.get(0));
        UsbDevice device = null;
        if (deviceList.size() > id) {
            device = deviceList.get(id);
        }
        if (device != null) {
            Log.v(TAG, "open :" + device.toString());
            mUSBMonitor.requestPermission(device);
            return true;
        } else {
            Log.v(TAG, "open null:");
            return false;
        }

    }

    public void close() {
        mHandler.closeCamera();
    }

    public boolean isCameraOpened() {
        return mHandler.isCameraOpened();
    }

    public void captureStill() {
        mHandler.captureStill();
    }

    public void startRecording(){
        mHandler.startRecording();
    }

    public void stopRecording(){
        mHandler.stopRecording();
    }

    public boolean isRecording(){
        return mHandler.isRecording();
    }
    private final USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            Log.v(TAG, "onAttach:");
            Toast.makeText(context, "onAttach", Toast.LENGTH_SHORT).show();
            usbStatus = USBStatus.ATTACHED;
        }

        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
            Log.v(TAG, "onConnect:");
            Toast.makeText(context, "onConnect", Toast.LENGTH_SHORT).show();
            usbStatus = USBStatus.CONNECTED;
            mHandler.openCamera(ctrlBlock);
            mHandler.startPreview(new Surface(mSurfaceTexture));
        }

        @Override
        public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
            Toast.makeText(context, "onDisconnect", Toast.LENGTH_SHORT).show();
            Log.v(TAG, "onDisconnect:");
            usbStatus = USBStatus.DISCONNECTED;
            if (mHandler != null) {
                mHandler.closeCamera();
            }
        }

        @Override
        public void onDetach(final UsbDevice device) {
            Toast.makeText(context, "onDetach", Toast.LENGTH_SHORT).show();
            Log.v(TAG, "onDetach:");
            usbStatus = USBStatus.DETACHED;
        }

        @Override
        public void onCancel() {
        }
    };
}
