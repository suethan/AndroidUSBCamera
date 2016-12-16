package com.dreamguard.usb.camera;

/**
 * Created by hailin.dai on 12/2/16.
 * email:hailin.dai@wz-tech.com
 */

import android.content.Context;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaScannerConnection;
import android.media.SoundPool;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Surface;


import com.dreamguard.api.R;
import com.dreamguard.encoder.MediaMuxerRunnable;
import com.dreamguard.usb.detect.USBMonitor;
import com.dreamguard.util.ImageProc;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;

/**
 * Handler class to execute camera releated methods sequentially on private thread
 */
public class CameraHandler extends Handler {

    private static final boolean DEBUG = true;
    private static final String TAG = "CameraHandler";

    /**
     * preview resolution(width)
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     */
    public static int PREVIEW_WIDTH = 640;
    /**
     * preview resolution(height)
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     */
    public static int PREVIEW_HEIGHT = 480;
    /**
     * preview mode
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     * 0:YUYV, other:MJPEG
     */

    public static int RECORD_WIDTH = 640;

    public static int RECORD_HEIGHT = 480;

    public static int CAPTURE_WIDTH = 640;

    public static int CAPTURE_HEIGHT = 480;

    public static boolean is3D = false;

    private static final int PREVIEW_MODE = 1;


    private static final int MSG_OPEN = 0;
    private static final int MSG_CLOSE = 1;
    private static final int MSG_PREVIEW_START = 2;
    private static final int MSG_PREVIEW_STOP = 3;
    private static final int MSG_CAPTURE_STILL = 4;
    private static final int MSG_CAPTURE_START = 5;
    private static final int MSG_CAPTURE_STOP = 6;
    private static final int MSG_MEDIA_UPDATE = 7;
    private static final int MSG_RELEASE = 9;

    private final WeakReference<CameraThread> mWeakThread;

    public static final CameraHandler createHandler(final Context parent) {
        final CameraThread thread = new CameraThread(parent);
        thread.start();
        return thread.getHandler();
    }

    private CameraHandler(final CameraThread thread) {
        mWeakThread = new WeakReference<CameraThread>(thread);
    }

    public boolean isCameraOpened() {
        final CameraThread thread = mWeakThread.get();
        return thread != null ? thread.isCameraOpened() : false;
    }

    public boolean isRecording() {
        final CameraThread thread = mWeakThread.get();
        return thread != null ? thread.isRecording() :false;
    }

    public void openCamera(final USBMonitor.UsbControlBlock ctrlBlock) {
        sendMessage(obtainMessage(MSG_OPEN, ctrlBlock));
    }

    public void closeCamera() {
        stopPreview();
        sendEmptyMessage(MSG_CLOSE);
    }

    public void startPreview(final Surface sureface) {
        if (sureface != null)
            sendMessage(obtainMessage(MSG_PREVIEW_START, sureface));
    }

    public void stopPreview() {
        stopRecording();
        final CameraThread thread = mWeakThread.get();
        if (thread == null) return;
        synchronized (thread.mSync) {
            sendEmptyMessage(MSG_PREVIEW_STOP);
            // wait for actually preview stopped to avoid releasing Surface/SurfaceTexture
            // while preview is still running.
            // therefore this method will take a time to execute
            try {
                thread.mSync.wait();
            } catch (final InterruptedException e) {
            }
        }
    }

    public void captureStill() {
        sendEmptyMessage(MSG_CAPTURE_STILL);
    }

    public void startRecording() {
        sendEmptyMessage(MSG_CAPTURE_START);
    }

    public void stopRecording() {
        sendEmptyMessage(MSG_CAPTURE_STOP);
    }

/*		public void release() {
			sendEmptyMessage(MSG_RELEASE);
		} */

    @Override
    public void handleMessage(final Message msg) {
        final CameraThread thread = mWeakThread.get();
        if (thread == null) return;
        switch (msg.what) {
            case MSG_OPEN:
                thread.handleOpen((USBMonitor.UsbControlBlock)msg.obj);
                break;
            case MSG_CLOSE:
                thread.handleClose();
                break;
            case MSG_PREVIEW_START:
                thread.handleStartPreview((Surface)msg.obj);
                break;
            case MSG_PREVIEW_STOP:
                thread.handleStopPreview();
                break;
            case MSG_CAPTURE_STILL:
                thread.handleCaptureStill();
                break;
            case MSG_CAPTURE_START:
                thread.handleStartRecording();
                break;
            case MSG_CAPTURE_STOP:
                thread.handleStopRecording();
                break;
            case MSG_MEDIA_UPDATE:
                thread.handleUpdateMedia((String)msg.obj);
                break;
            case MSG_RELEASE:
                thread.handleRelease();
                break;
            default:
                throw new RuntimeException("unsupported message:what=" + msg.what);
        }
    }


    private static final class CameraThread extends Thread {
        private static final String TAG_THREAD = "CameraThread";
        private final Object mSync = new Object();
        private final WeakReference<Context> mWeakParent;
        private boolean mIsRecording = false;
        private boolean isCaptureStill = false;
        /**
         * shutter sound
         */
        private SoundPool mSoundPool;
        private int mSoundId;
        private CameraHandler mHandler;
        /**
         * for accessing UVC camera
         */
        private UVCCamera mUVCCamera;
        /**
         * muxer for audio/video recording
         */



        private CameraThread(final Context parent) {
            super("CameraThread");
            mWeakParent = new WeakReference<Context>(parent);
            loadSutterSound(parent);
        }

        @Override
        protected void finalize() throws Throwable {
            Log.i(TAG, "CameraThread#finalize");
            super.finalize();
        }

        public CameraHandler getHandler() {
            if (DEBUG) Log.v(TAG_THREAD, "getHandler:");
            synchronized (mSync) {
                if (mHandler == null)
                    try {
                        mSync.wait();
                    } catch (final InterruptedException e) {
                    }
            }
            return mHandler;
        }

        public boolean isCameraOpened() {
            return mUVCCamera != null;
        }

        public boolean isRecording() {
            return mIsRecording;
        }

        public void handleOpen(final USBMonitor.UsbControlBlock ctrlBlock) {
            if (DEBUG) Log.v(TAG_THREAD, "handleOpen:");
//            handleClose();
            mUVCCamera = new UVCCamera();
            mUVCCamera.open(ctrlBlock);
            if (DEBUG) Log.i(TAG, "supportedSize:" + mUVCCamera.getSupportedSize());
        }

        public void handleClose() {
            if (DEBUG) Log.v(TAG_THREAD, "handleClose:");
            handleStopRecording();
            if (mUVCCamera != null) {
                mUVCCamera.stopPreview();
                mUVCCamera.destroy();
                mUVCCamera = null;
            }
        }

        public void handleStartPreview(final Surface surface) {
            if (DEBUG) Log.v(TAG_THREAD, "handleStartPreview:");
            if (mUVCCamera == null) return;
            try {
                mUVCCamera.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_MODE);

            } catch (final IllegalArgumentException e) {
                try {
                    // fallback to YUV mode
                    mUVCCamera.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT, UVCCamera.DEFAULT_PREVIEW_MODE);
                } catch (final IllegalArgumentException e1) {
                    handleClose();
                }
            }
            if (mUVCCamera != null) {
                if(is3D) {
                    CAPTURE_WIDTH = PREVIEW_WIDTH/2;
                    RECORD_WIDTH = PREVIEW_WIDTH/2;
                    mUVCCamera.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_NV21_HALF);
                }else {
                    mUVCCamera.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_NV21);

                }
                mUVCCamera.setPreviewDisplay(surface);
                mUVCCamera.startPreview();
            }
        }

        public void handleStopPreview() {
            if (DEBUG) Log.v(TAG_THREAD, "handleStopPreview:");
            if (mUVCCamera != null) {
                mUVCCamera.stopPreview();
            }
            synchronized (mSync) {
                mSync.notifyAll();
            }
        }

        public void handleCaptureStill() {
            isCaptureStill = true;
            if (DEBUG) Log.v(TAG_THREAD, "handleCaptureStill:");
        }

        public void handleStartRecording() {
            if (DEBUG) Log.v(TAG_THREAD, "handleStartRecording:");
            mIsRecording = true;
            //start

            MediaMuxerRunnable.startMuxer();


        }

        public void handleStopRecording() {
            if (DEBUG) Log.v(TAG_THREAD, "handleStopRecording:");
            if(mIsRecording) {
                //stop
                MediaMuxerRunnable.stopMuxer();
            }
            mIsRecording = false;
        }

        public void handleUpdateMedia(final String path) {
            if (DEBUG) Log.v(TAG_THREAD, "handleUpdateMedia:path=" + path);
            final Context parent = mWeakParent.get();
            if (parent != null && parent.getApplicationContext() != null) {
                try {
                    if (DEBUG) Log.i(TAG, "MediaScannerConnection#scanFile");
                    MediaScannerConnection.scanFile(parent.getApplicationContext(), new String[]{ path }, null, null);
                } catch (final Exception e) {
                    Log.e(TAG, "handleUpdateMedia:", e);
                }
            } else {
                Log.w(TAG, "MainActivity already destroyed");
                // give up to add this movice to MediaStore now.
                // Seeing this movie on Gallery app etc. will take a lot of time.
                handleRelease();
            }
        }

        public void handleRelease() {
            if (DEBUG) Log.v(TAG_THREAD, "handleRelease:");
            handleClose();
            if (!mIsRecording)
                Looper.myLooper().quit();
        }


        /**
         * prepare and load shutter sound for still image capturing
         */
        @SuppressWarnings("deprecation")
        private void loadSutterSound(final Context context) {
            // get system stream type using refrection
            int streamType;
            try {
                final Class<?> audioSystemClass = Class.forName("android.media.AudioSystem");
                final Field sseField = audioSystemClass.getDeclaredField("STREAM_SYSTEM_ENFORCED");
                streamType = sseField.getInt(null);
            } catch (final Exception e) {
                streamType = AudioManager.STREAM_SYSTEM;	// set appropriate according to your app policy
            }
            if (mSoundPool != null) {
                try {
                    mSoundPool.release();
                } catch (final Exception e) {
                }
                mSoundPool = null;
            }
            // load sutter sound from resource
            mSoundPool = new SoundPool(2, streamType, 0);
            mSoundId = mSoundPool.load(context, R.raw.camera_click, 1);
        }

        private void captureStill(ByteBuffer frame){
            Log.d(TAG,"onFrame Capture still");
            mSoundPool.play(mSoundId, 0.2f, 0.2f, 0, 0, 1.0f);
            File outputFile = null;
            BufferedOutputStream os = null;
            int rgb[] = new int[CAPTURE_WIDTH*CAPTURE_HEIGHT];
            try {
                outputFile = new File(Environment.getExternalStorageDirectory().getPath() + "/K3DX/" + System.currentTimeMillis() + ".jpg");
                os = new BufferedOutputStream(new FileOutputStream(outputFile));
                byte buf[] = new byte[CAPTURE_WIDTH*CAPTURE_HEIGHT*3/2];
                frame.get(buf);
                ImageProc.decodeYUV420SP(rgb,buf,CAPTURE_WIDTH,CAPTURE_HEIGHT);
                Bitmap bitmap = Bitmap.createBitmap(rgb,CAPTURE_WIDTH, CAPTURE_HEIGHT, Bitmap.Config.ARGB_8888);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
                os.flush();
                os.close();
                mHandler.sendMessage(mHandler.obtainMessage(MSG_MEDIA_UPDATE, outputFile.getPath()));
            } catch (Exception e){
                Log.e(TAG,"onFrame Capture still Error Exception");
            }

            isCaptureStill = false;
        }

        // if you need frame data as ByteBuffer on Java side, you can use this callback method with UVCCamera#setFrameCallback
        private final IFrameCallback mIFrameCallback = new IFrameCallback() {
            @Override
            public void onFrame(final ByteBuffer frame) {

                if(isCaptureStill) {
                    captureStill(frame);
                }
                if(isRecording()){
                    long startTime = System.currentTimeMillis();
                    byte buf[] = new byte[PREVIEW_WIDTH/2*PREVIEW_HEIGHT*3/2];
                    frame.get(buf);
//                    videoEncoder.encodeFrame(buf);
                    MediaMuxerRunnable.addVideoFrameData(buf);
                    long endTime = System.currentTimeMillis();
                    Log.i(TAG, Integer.toString((int)(endTime-startTime)) + "ms");
                }


            }
        };

        @Override
        public void run() {
            Looper.prepare();
            synchronized (mSync) {
                mHandler = new CameraHandler(this);
                mSync.notifyAll();
            }
            Looper.loop();
            synchronized (mSync) {
                mHandler = null;
                mSoundPool.release();
                mSoundPool = null;
                mSync.notifyAll();
            }
        }
    }
}