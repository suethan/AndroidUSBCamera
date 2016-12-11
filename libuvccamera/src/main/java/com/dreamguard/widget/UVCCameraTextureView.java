package com.dreamguard.widget;
/*
 * UVCCamera
 * library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2015 saki t_saki@serenegiant.com
 *
 * File name: UVCCameraTextureView.java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
 * Files in the jni/libjpeg, jni/libusb, jin/libuvc, jni/rapidjson folder may have a different license, see the respective files.
*/

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;

import com.dreamguard.renderer.EGLBase;
import com.dreamguard.renderer.GLDrawer2D;


/**
 * change the view size with keeping the specified aspect ratio.
 * if you set this view with in a FrameLayout and set property "android:layout_gravity="center",
 * you can show this view in the center of screen and keep the aspect ratio of content
 * XXX it is better that can set the aspect raton a a xml property
 */
public class UVCCameraTextureView extends TextureView	// API >= 14
	implements TextureView.SurfaceTextureListener, CameraViewInterface {

	private static final boolean DEBUG = true;	// TODO set false on release
	private static final String TAG = "UVCCameraTextureView";

    private double mRequestedAspect = -1.0;
    private boolean mHasSurface;
    private RenderHandler mRenderHandler;
    private final Object mCaptureSync = new Object();
    private Bitmap mTempBitmap;
    private boolean mReqesutCaptureStillImage;

	public UVCCameraTextureView(final Context context) {
		this(context, null, 0);
	}

	public UVCCameraTextureView(final Context context, final AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public UVCCameraTextureView(final Context context, final AttributeSet attrs, final int defStyle) {
		super(context, attrs, defStyle);
		setSurfaceTextureListener(this);
	}

	@Override
	public void onResume() {
		if (DEBUG) Log.v(TAG, "onResume:");
		if (mHasSurface) {
			mRenderHandler = RenderHandler.createHandler(super.getSurfaceTexture());
		}
	}

	@Override
	public void onPause() {
		if (DEBUG) Log.v(TAG, "onPause:");
		if (mRenderHandler != null) {
			mRenderHandler.release();
			mRenderHandler = null;
		}
		if (mTempBitmap != null) {
			mTempBitmap.recycle();
			mTempBitmap = null;
		}
	}

	@Override
    public void setAspectRatio(final double aspectRatio) {
        if (aspectRatio < 0) {
            throw new IllegalArgumentException();
        }
        if (mRequestedAspect != aspectRatio) {
            mRequestedAspect = aspectRatio;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

		if (mRequestedAspect > 0) {
			int initialWidth = MeasureSpec.getSize(widthMeasureSpec);
			int initialHeight = MeasureSpec.getSize(heightMeasureSpec);

			final int horizPadding = getPaddingLeft() + getPaddingRight();
			final int vertPadding = getPaddingTop() + getPaddingBottom();
			initialWidth -= horizPadding;
			initialHeight -= vertPadding;

			final double viewAspectRatio = (double)initialWidth / initialHeight;
			final double aspectDiff = mRequestedAspect / viewAspectRatio - 1;

			if (Math.abs(aspectDiff) > 0.01) {
				if (aspectDiff > 0) {
					// width priority decision
					initialHeight = (int) (initialWidth / mRequestedAspect);
				} else {
					// height priority decison
					initialWidth = (int) (initialHeight * mRequestedAspect);
				}
				initialWidth += horizPadding;
				initialHeight += vertPadding;
				widthMeasureSpec = MeasureSpec.makeMeasureSpec(initialWidth, MeasureSpec.EXACTLY);
				heightMeasureSpec = MeasureSpec.makeMeasureSpec(initialHeight, MeasureSpec.EXACTLY);
			}
		}

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

	@Override
	public void onSurfaceTextureAvailable(final SurfaceTexture surface, final int width, final int height) {
		if (DEBUG) Log.v(TAG, "onSurfaceTextureAvailable:" + surface);
		mRenderHandler = RenderHandler.createHandler(surface);
		mHasSurface = true;
	}

	@Override
	public void onSurfaceTextureSizeChanged(final SurfaceTexture surface, final int width, final int height) {
		if (DEBUG) Log.v(TAG, "onSurfaceTextureSizeChanged:" + surface);
	}

	@Override
	public boolean onSurfaceTextureDestroyed(final SurfaceTexture surface) {
		if (DEBUG) Log.v(TAG, "onSurfaceTextureDestroyed:" + surface);
		if (mRenderHandler != null) {
			mRenderHandler.release();
			mRenderHandler = null;
		}
		mHasSurface = false;
		return true;
	}

	@Override
	public void onSurfaceTextureUpdated(final SurfaceTexture surface) {
		synchronized (mCaptureSync) {
			if (mReqesutCaptureStillImage) {
				mReqesutCaptureStillImage = false;
				if (mTempBitmap == null)
					mTempBitmap = getBitmap();
				else
					getBitmap(mTempBitmap);
				mCaptureSync.notifyAll();
			}
		}
	}

	@Override
	public boolean hasSurface() {
		return mHasSurface;
	}


	@Override
	public SurfaceTexture getSurfaceTexture() {
		return mRenderHandler != null ? mRenderHandler.getPreviewTexture() : super.getSurfaceTexture();
	}


	/**
	 * render camera frames on this view on a private thread
	 * @author saki
	 *
	 */
	private static final class RenderHandler extends Handler
		implements SurfaceTexture.OnFrameAvailableListener  {

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

/*
 * Now you can get frame data as ByteBuffer(as YUV/RGB565/RGBX/NV21 pixel format) using IFrameCallback interface
 * with UVCCamera#setFrameCallback instead of using following code samples.
 */
/*			// for part1
 			private static final int BUF_NUM = 1;
			private static final int BUF_STRIDE = 640 * 480;
			private static final int BUF_SIZE = BUF_STRIDE * BUF_NUM;
			int cnt = 0;
			int offset = 0;
			final int pixels[] = new int[BUF_SIZE];
			final IntBuffer buffer = IntBuffer.wrap(pixels); */
/*			// for part2
			private ByteBuffer buf = ByteBuffer.allocateDirect(640 * 480 * 4);
 */
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

/*			// sample code to read pixels into IntBuffer and save as a Bitmap (part1)
			private static Bitmap createBitmap(final int[] pixels, final int offset, final int width, final int height) {
				final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
				paint.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[] {
						0, 0, 1, 0, 0,
						0, 1, 0, 0, 0,
						1, 0, 0, 0, 0,
						0, 0, 0, 1, 0
					})));

				final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
				final Canvas canvas = new Canvas(bitmap);

				final Matrix matrix = new Matrix();
				matrix.postScale(1.0f, -1.0f);
				matrix.postTranslate(0, height);
				canvas.concat(matrix);

				canvas.drawBitmap(pixels, offset, width, 0, 0, width, height, false, paint);

				return bitmap;
			} */

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
}
