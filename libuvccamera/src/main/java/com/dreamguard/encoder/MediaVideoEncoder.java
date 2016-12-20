package com.dreamguard.encoder;
/*
 * AudioVideoRecordingSample
 * Sample project to cature audio and video from internal mic/camera and save as MPEG4 file.
 *
 * Copyright (c) 2014-2015 saki t_saki@serenegiant.com
 *
 * File name: MediaVideoEncoder.java
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
*/

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.opengl.EGLContext;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MediaVideoEncoder extends MediaEncoder {
	private static final boolean DEBUG = false;	// TODO set false on release
	private static final String TAG = "MediaVideoEncoder";

	private static final String MIME_TYPE = "video/avc";
	// parameters for recording
    private static final int FRAME_RATE = 25;
    private static final float BPP = 0.25f;

    private final int mWidth;
    private final int mHeight;
    private Surface mSurface;
	byte[] mFrameData;
	private int mColorFormat;

	public MediaVideoEncoder(final MediaMuxerWrapper muxer, final MediaEncoderListener listener, final int width, final int height) {
		super(muxer, listener);
		if (DEBUG) Log.i(TAG, "MediaVideoEncoder: ");
		mWidth = width;
		mHeight = height;
		mFrameData = new byte[this.mWidth * this.mHeight * 3 / 2];
	}

	@Override
	public boolean frameAvailableSoon() {
		boolean result;
		if (result = super.frameAvailableSoon()){

		}
		return result;
	}

	@Override
	protected void prepare() throws IOException {
		if (DEBUG) Log.i(TAG, "prepare: ");
        mTrackIndex = -1;
        mMuxerStarted = mIsEOS = false;

        final MediaCodecInfo videoCodecInfo = selectVideoCodec(MIME_TYPE);
        if (videoCodecInfo == null) {
            Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
            return;
        }
		if (DEBUG) Log.i(TAG, "selected codec: " + videoCodecInfo.getName());

		mColorFormat = selectColorFormat(videoCodecInfo, MIME_TYPE);
		if (DEBUG)
			Log.d(TAG, "found colorFormat: " + mColorFormat);

        final MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, mColorFormat);
        format.setInteger(MediaFormat.KEY_BIT_RATE, calcBitRate());
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
		if (DEBUG) Log.i(TAG, "format: " + format);

		boolean ret = videoCodecInfo.getCapabilitiesForType(MIME_TYPE).isFormatSupported(format);

        mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        // get Surface for encoder input
        // this method only can call between #configure and #start
//        mSurface = mMediaCodec.createInputSurface();	// API >= 18
        mMediaCodec.start();
        if (DEBUG) Log.i(TAG, "prepare finishing");
        if (mListener != null) {
        	try {
        		mListener.onPrepared(this);
        	} catch (final Exception e) {
        		Log.e(TAG, "prepare:", e);
        	}
        }

		mStartTime = System.nanoTime();
	}

	private long mStartTime;

	public void encodeFrame(byte[] input/* , byte[] output */) {
		Log.i(TAG, "encodeFrame()");
		long encodedSize = 0;
		NV21toI420SemiPlanar(input, mFrameData, this.mWidth, this.mHeight);

		ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();

		int inputBufferIndex = mMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
		if (DEBUG)
			Log.i(TAG, "inputBufferIndex-->" + inputBufferIndex);
		if (inputBufferIndex >= 0) {
			long endTime = System.nanoTime();
			long ptsUsec = (endTime - mStartTime) / 1000;
			Log.i(TAG, "resentationTime: " + ptsUsec);
			ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
			inputBuffer.clear();
			inputBuffer.put(mFrameData);
			mMediaCodec.queueInputBuffer(inputBufferIndex, 0,
					mFrameData.length, System.nanoTime() / 1000, 0);
		} else {
			// either all in use, or we timed out during initial setup
			if (DEBUG)
				Log.d(TAG, "input buffer not available");
		}

		frameAvailableSoon();

	}

	/**
	 * NV21 is a 4:2:0 YCbCr, For 1 NV21 pixel: YYYYYYYY VUVU I420YUVSemiPlanar
	 * is a 4:2:0 YUV, For a single I420 pixel: YYYYYYYY UVUV Apply NV21 to
	 * I420YUVSemiPlanar(NV12) Refer to https://wiki.videolan.org/YUV/
	 */
	private void NV21toI420SemiPlanar(byte[] nv21bytes, byte[] i420bytes,
									  int width, int height) {
		System.arraycopy(nv21bytes, 0, i420bytes, 0, width * height);
		for (int i = width * height; i < nv21bytes.length; i += 2) {
			i420bytes[i] = nv21bytes[i + 1];
			i420bytes[i + 1] = nv21bytes[i];
		}
	}

	public void setEglContext(final EGLContext shared_context, final int tex_id) {
	}

	@Override
    protected void release() {
		if (DEBUG) Log.i(TAG, "release:");
		if (mSurface != null) {
			mSurface.release();
			mSurface = null;
		}
		super.release();
	}

	private int calcBitRate() {
		final int bitrate = (int)(BPP * FRAME_RATE * mWidth * mHeight);
		Log.i(TAG, String.format("bitrate=%5.2f[Mbps]", bitrate / 1024f / 1024f));
		return bitrate;
	}

    /**
     * select the first codec that match a specific MIME type
     * @param mimeType
     * @return null if no codec matched
     */
    protected static final MediaCodecInfo selectVideoCodec(final String mimeType) {
    	if (DEBUG) Log.v(TAG, "selectVideoCodec:");

    	// get the list of available codecs
        final int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
        	final MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if (!codecInfo.isEncoder()) {	// skipp decoder
                continue;
            }
            // select first codec that match a specific MIME type and color format
            final String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                	if (DEBUG) Log.i(TAG, "codec:" + codecInfo.getName() + ",MIME=" + types[j]);
					return codecInfo;
                }
            }
        }
        return null;
    }

	/**
	 * Returns a color format that is supported by the codec and by this test
	 * code. If no match is found, this throws a test failure -- the set of
	 * formats known to the test should be expanded for new platforms.
	 */
	private static int selectColorFormat(MediaCodecInfo codecInfo,
										 String mimeType) {
		MediaCodecInfo.CodecCapabilities capabilities = codecInfo
				.getCapabilitiesForType(mimeType);

		for (int i = 0; i < capabilities.colorFormats.length; i++) {
			int colorFormat = capabilities.colorFormats[i];
			if (isRecognizedFormat(colorFormat)) {
				return colorFormat;
			}
		}
		Log.e(TAG,
				"couldn't find a good color format for " + codecInfo.getName()
						+ " / " + mimeType);
		return 0; // not reached
	}

	/**
	 * Returns true if this is a color format that this test code understands
	 * (i.e. we know how to read and generate frames in this format).
	 */
	private static boolean isRecognizedFormat(int colorFormat) {
		switch (colorFormat) {
			// these are the formats we know how to handle for this test
			case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
			case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
			case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
			case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
			case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
				return true;
			default:
				return false;
		}
	}

}
