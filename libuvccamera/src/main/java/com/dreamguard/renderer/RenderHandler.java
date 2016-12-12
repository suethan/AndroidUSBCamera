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

    private RenderThread mThread;
    private boolean mIsActive = true;

    public static final RenderHandler createHandler(final SurfaceTexture surface) {
        final RenderThread thread = new RenderThread(surface);
        thread.start();
        return thread.getHandler();
    }

    private RenderHandler(final RenderThread thread) {
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

    private static final class RenderThread extends Thread {
        private final Object mSync = new Object();
        private final SurfaceTexture mSurface;
        private RenderHandler mHandler;
        private EGLBase mEgl;
        private EGLBase.EglSurface mEglSurface;
        private GLDrawer2D mDrawer;
        private int mTexId = -1;
        private SurfaceTexture mPreviewSurface;
        private final float[] mStMatrix = new float[16];

        /**
         * constructor
         *
         * @param surface: drawing surface came from TexureView
         */
        public RenderThread(final SurfaceTexture surface) {
            mSurface = surface;
            setName("RenderThread");
        }

        public final RenderHandler getHandler() {
            if (DEBUG) Log.v(TAG, "RenderThread#getHandler:");
            synchronized (mSync) {
                // create rendering thread
                if (mHandler == null)
                    try {
                        mSync.wait();
                    } catch (final InterruptedException e) {
                    }
            }
            return mHandler;
        }

        public final void updatePreviewSurface() {
            if (DEBUG) Log.i(TAG, "RenderThread#updatePreviewSurface:");
            synchronized (mSync) {
                if (mPreviewSurface != null) {
                    if (DEBUG) Log.d(TAG, "release mPreviewSurface");
                    mPreviewSurface.setOnFrameAvailableListener(null);
                    mPreviewSurface.release();
                    mPreviewSurface = null;
                }
                mEglSurface.makeCurrent();
                if (mTexId >= 0) {
                    GLDrawer2D.deleteTex(mTexId);
                }
                // create texture and SurfaceTexture for input from camera
                mTexId = GLDrawer2D.initTex();
                if (DEBUG) Log.v(TAG, "getPreviewSurface:tex_id=" + mTexId);
                mPreviewSurface = new SurfaceTexture(mTexId);
                mPreviewSurface.setOnFrameAvailableListener(mHandler);
                // notify to caller thread that previewSurface is ready
                mSync.notifyAll();
            }
        }

        /**
         * draw a frame (and request to draw for video capturing if it is necessary)
         */
        public final void onDrawFrame() {
            mEglSurface.makeCurrent();
            // update texture(came from camera)
            mPreviewSurface.updateTexImage();
            // get texture matrix
            mPreviewSurface.getTransformMatrix(mStMatrix);
            // draw to preview screen
            mDrawer.draw(mTexId, mStMatrix);
            mEglSurface.swap();

        }

        @Override
        public final void run() {
            Log.d(TAG, getName() + " started");
            init();
            Looper.prepare();
            synchronized (mSync) {
                mHandler = new RenderHandler(this);
                mSync.notify();
            }

            Looper.loop();

            Log.d(TAG, getName() + " finishing");
            release();
            synchronized (mSync) {
                mHandler = null;
                mSync.notify();
            }
        }

        private final void init() {
            if (DEBUG) Log.v(TAG, "RenderThread#init:");
            // create EGLContext for this thread
            mEgl = new EGLBase(null, false, false);
            mEglSurface = mEgl.createFromSurface(mSurface);
            mEglSurface.makeCurrent();
            // create drawing object
            mDrawer = new GLDrawer2D();
        }

        private final void release() {
            if (DEBUG) Log.v(TAG, "RenderThread#release:");
            if (mDrawer != null) {
                mDrawer.release();
                mDrawer = null;
            }
            if (mPreviewSurface != null) {
                mPreviewSurface.release();
                mPreviewSurface = null;
            }
            if (mTexId >= 0) {
                GLDrawer2D.deleteTex(mTexId);
                mTexId = -1;
            }
            if (mEglSurface != null) {
                mEglSurface.release();
                mEglSurface = null;
            }
            if (mEgl != null) {
                mEgl.release();
                mEgl = null;
            }
        }
    }
}

