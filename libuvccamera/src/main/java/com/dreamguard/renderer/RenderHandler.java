package com.dreamguard.renderer;

import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

/**
 * Created by hailin.dai on 12/12/16.
 * email:hailin.dai@wz-tech.com
 */

public class RenderHandler extends Handler
        implements SurfaceTexture.OnFrameAvailableListener {

    private static final boolean DEBUG = true;    // TODO set false on release
    private static final String TAG = "RenderHandler";

    private static final int MSG_REQUEST_RENDER = 1;
    private static final int MSG_SET_ENCODER = 2;
    private static final int MSG_CREATE_SURFACE = 3;
    private static final int MSG_TERMINATE = 9;

    private RendererThread mThread;
    private boolean mIsActive = true;

    public static final RenderHandler createHandler(final SurfaceTexture surface) {
        final RendererThread thread = new RendererThread(surface);
        thread.start();
        return thread.getHandler();
    }

    public RenderHandler(RendererThread thread) {
        mThread = thread;
    }


    public final SurfaceTexture getPreviewTexture() {
        if (DEBUG) Log.v(TAG, "getPreviewTexture:");
        synchronized (mThread.mSync) {
            sendEmptyMessage(MSG_CREATE_SURFACE);
            try {
                mThread.mSync.wait();
            } catch (final InterruptedException e) {
            }
            if (DEBUG) Log.v(TAG, "getPreviewTexture:" + mThread.mPreviewSurface);
            return mThread.mPreviewSurface;
        }
    }


    public final void release() {
        if (DEBUG) Log.v(TAG, "release:");
        if (mIsActive) {
            mIsActive = false;
            removeMessages(MSG_REQUEST_RENDER);
            removeMessages(MSG_SET_ENCODER);
            sendEmptyMessage(MSG_TERMINATE);
        }
    }

    @Override
    public final void onFrameAvailable(final SurfaceTexture surfaceTexture) {
        if (mIsActive)
            sendEmptyMessage(MSG_REQUEST_RENDER);
    }

    @Override
    public final void handleMessage(final Message msg) {
        if (mThread == null) return;
        switch (msg.what) {
            case MSG_REQUEST_RENDER:
                mThread.onDrawFrame();
                break;
            case MSG_SET_ENCODER:
                break;
            case MSG_CREATE_SURFACE:
                mThread.updatePreviewSurface();
                break;
            case MSG_TERMINATE:
                Looper.myLooper().quit();
                mThread = null;
                break;
            default:
                super.handleMessage(msg);
        }
    }
}

