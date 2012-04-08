/*
   Copyright 2012 Harri Smatt

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package fi.harism.wallpaper.lovebeat;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;

import android.opengl.GLSurfaceView;

/**
 * EGLConfigChooser for selecting highest available RGB precision plus depth
 * buffer precision if needed. As for alpha and stencil buffer (and depth buffer
 * if set to false), lowest ones are preferred.
 */
public final class LBEGLConfigChooser implements GLSurfaceView.EGLConfigChooser {

	private boolean mNeedsDepth;

	/**
	 * Default constructor.
	 * 
	 * @param needsDepth
	 *            If true, will try to find config with highest depth buffer
	 *            precision on top of RGB precision.
	 */
	public LBEGLConfigChooser(boolean needsDepth) {
		mNeedsDepth = needsDepth;
	}

	@Override
	public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
		int[] configSpec = { EGL10.EGL_RED_SIZE, 4, EGL10.EGL_GREEN_SIZE, 4,
				EGL10.EGL_BLUE_SIZE, 4, EGL10.EGL_ALPHA_SIZE, 0,
				EGL10.EGL_DEPTH_SIZE, 0, EGL10.EGL_STENCIL_SIZE, 0,
				EGL10.EGL_NONE };

		int[] temp = new int[1];
		egl.eglChooseConfig(display, configSpec, null, 0, temp);
		int numConfigs = temp[0];
		if (numConfigs <= 0) {
			throw new RuntimeException("No configs found.");
		}
		EGLConfig[] configs = new EGLConfig[numConfigs];
		egl.eglChooseConfig(display, configSpec, configs, numConfigs, temp);

		int highestSum = 0;
		int highestSub = 0;
		EGLConfig highestConfig = null;
		for (EGLConfig config : configs) {
			int r = getConfigAttrib(egl, display, config, EGL10.EGL_RED_SIZE,
					0, temp);
			int g = getConfigAttrib(egl, display, config, EGL10.EGL_GREEN_SIZE,
					0, temp);
			int b = getConfigAttrib(egl, display, config, EGL10.EGL_BLUE_SIZE,
					0, temp);
			int d = getConfigAttrib(egl, display, config, EGL10.EGL_DEPTH_SIZE,
					0, temp);
			int a = getConfigAttrib(egl, display, config, EGL10.EGL_ALPHA_SIZE,
					0, temp);
			int s = getConfigAttrib(egl, display, config,
					EGL10.EGL_STENCIL_SIZE, 0, temp);

			int sum = r + g + b + (mNeedsDepth ? d : 0);
			int sub = a + s;

			if (sum > highestSum || (sum == highestSum && sub < highestSub)) {
				highestSum = sum;
				highestSub = sub;
				highestConfig = config;
			}
		}
		if (highestConfig == null) {
			throw new RuntimeException(
					"No config chosen, this should never happen.");
		}
		return highestConfig;
	}

	/**
	 * Getter for single attribute value from EGLConfig.
	 */
	private int getConfigAttrib(EGL10 egl, EGLDisplay display,
			EGLConfig config, int attribute, int defaultValue, int[] temp) {
		if (egl.eglGetConfigAttrib(display, config, attribute, temp)) {
			return temp[0];
		}
		return defaultValue;
	}
}
