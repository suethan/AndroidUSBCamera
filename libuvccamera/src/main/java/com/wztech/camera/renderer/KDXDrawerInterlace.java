package com.wztech.camera.renderer;
/*
 * UVCCamera
 * library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2015 saki t_saki@serenegiant.com
 *
 * File name: GLDrawerBase.java
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

import android.opengl.GLES20;

import com.dreamguard.renderer.GLDrawerBase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Exchanger;

/**
 * Helper class to draw to whole view using specific texture and texture matrix
 */
public class KDXDrawerInterlace extends GLDrawerBase {
	private static final boolean DEBUG = true; // TODO set false on release
	private static final String TAG = "GLDrawerBase";

	private static final String vss
		= "uniform mat4 uMVPMatrix;\n"
		+ "uniform mat4 uTexMatrix;\n"
		+ "attribute highp vec4 aPosition;\n"
		+ "attribute highp vec4 aTextureCoord;\n"
		+ "varying highp vec2 vTextureCoord;\n"
		+ "\n"
		+ "void main() {\n"
		+ "	gl_Position = uMVPMatrix * aPosition;\n"
		+ "	vTextureCoord = (uTexMatrix * aTextureCoord).xy;\n"
		+ "}\n";
	private static final String fss
		= "#extension GL_OES_EGL_image_external : require\n"
		+ "precision mediump float;\n"
		+ "uniform samplerExternalOES sTexture;\n"
		+ "uniform int uRendererMode;\n"
		+ "varying highp vec2 vTextureCoord;\n"
		+ "void main() {\n"
		+ "		vec2 tc = vTextureCoord;\n"
		+ "		float xx = floor(gl_FragCoord.x);\n"
		+ "		if(uRendererMode == 1){\n"
		+ "			if(mod(xx,2.0) <= 0.0001){\n"
		+ "				tc.s = tc.s*0.5 + 0.5;\n"
		+ "			}else{\n"
		+ "				tc.s = tc.s*0.5;"
		+ "			}\n"
		+ "		}\n"
		+ "  	gl_FragColor = texture2D(sTexture, tc);\n"
		+ "}";


	int muRendererMode;

	int mRendererMode;

	public KDXDrawerInterlace() {
		super();
	}

	@Override
	public String getVertexShader() {
		return vss;
	}

	@Override
	public String getFragmentShader() {
		return fss;
	}

	@Override
	public void initParam(int program) {
		super.initParam(program);
		muRendererMode = GLES20.glGetUniformLocation(program, "uRendererMode");
	}

	@Override
	public void setParam() {
		super.setParam();
		if (this.muRendererMode != -1) {
			GLES20.glUniform1i(this.muRendererMode, mRendererMode);
		}
	}

	@Override
	public void updateParam(HashMap<String,String> param) {
		if(param.containsKey("rendererMode")) {
			try {
				mRendererMode = Integer.valueOf(param.get("rendererMode"));
			}catch (Exception e){

			}

		}

	}
}
