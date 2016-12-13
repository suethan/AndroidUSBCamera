package com.dreamguard.renderer;

import android.graphics.SurfaceTexture;
import android.os.Looper;
import android.util.Log;


import java.util.HashMap;

/**
 * Created by hailin.dai on 12/12/16.
 * email:hailin.dai@wz-tech.com
 */

public class RendererThread extends Thread {

    private static final boolean DEBUG = true;    // TODO set false on release
    private static final String TAG = "RendererThread";


    protected final Object mSync = new Object();
    private final SurfaceTexture mSurface;
    private RenderHandler mHandler;
    private EGLBase mEgl;
    private EGLBase.EglSurface mEglSurface;
    private GLDrawerBase mDrawer;
    private int mTexId = -1;
    protected SurfaceTexture mPreviewSurface;
    private final float[] mStMatrix = new float[16];

    /**
     * constructor
     *
     * @param surface: drawing surface came from TexureView
     */
    public RendererThread(final SurfaceTexture surface) {
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
                GLDrawerBase.deleteTex(mTexId);
            }
            // create texture and SurfaceTexture for input from camera
            mTexId = GLDrawerBase.initTex();
            if (DEBUG) Log.v(TAG, "getPreviewSurface:tex_id=" + mTexId);
            mPreviewSurface = new SurfaceTexture(mTexId);
            mPreviewSurface.setOnFrameAvailableListener(mHandler);
            // notify to caller thread that previewSurface is ready
            mSync.notifyAll();
        }
    }

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

    public final void updateRendererParam(HashMap<String,String> param){

        mDrawer.updateParam(param);
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
        mDrawer = new GLDrawerBase();
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
            GLDrawerBase.deleteTex(mTexId);
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

