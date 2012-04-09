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

import java.nio.ByteBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.widget.Toast;

/**
 * Main renderer class.
 */
public final class LBRenderer implements GLSurfaceView.Renderer {

	// Application context.
	private Context mContext;
	// FBOs for offscreen rendering.
	private LBFbo mLBFbo = new LBFbo();
	// Fore- and background renderers.
	private final LBRendererBg mRendererBg = new LBRendererBg();
	private final LBRendererFg mRendererFg = new LBRendererFg();
	// Vertex buffer for full scene coordinates.
	private ByteBuffer mScreenVertices;
	// Flag for indicating whether shader compiler is supported.
	private final boolean[] mShaderCompilerSupported = new boolean[1];
	// Shader for copying offscreen texture on screen.
	private final LBShader mShaderCopy = new LBShader();
	// Initialize last render time so that on first render iteration environment
	// is being set up properly.
	private long mTimeLast = -1;
	// Animation tick timer start time in millis.
	private long mTimeTickStart = -1;
	// True once following touch events. Used for fading away from displacement
	// mapping and stopping animation timer for the time touch events are being
	// executed.
	private boolean mTouchFollow;
	// Two { x, y } tuples for touch start and current touch position.
	private final float mTouchPositions[] = new float[4];
	// Surface width and height.
	private int mWidth, mHeight;

	/**
	 * Default constructor.
	 */
	public LBRenderer(Context context) {
		mContext = context;

		// Create screen coordinates buffer.
		final byte SCREEN_COORDS[] = { -1, 1, -1, -1, 1, 1, 1, -1 };
		mScreenVertices = ByteBuffer.allocateDirect(2 * 4);
		mScreenVertices.put(SCREEN_COORDS).position(0);
	}

	@Override
	public synchronized void onDrawFrame(GL10 unused) {
		// If shader compiler is not supported, clear screen buffer only.
		if (mShaderCompilerSupported[0] == false) {
			GLES20.glClearColor(0, 0, 0, 1);
			GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
			return;
		}

		// Animation tick time length in millis.
		final long ANIMATION_TICK_TIME = 1000;
		long currentTime = SystemClock.uptimeMillis();
		boolean newTime = false;

		// If we're following touch events stop animation timer.
		if (mTouchFollow && mTimeLast >= 0) {
			mTimeTickStart += currentTime - mTimeLast;
		} else if (mTimeLast >= 0) {
			// Adjust "current touch position" towards start touch position in
			// order to hide displacement effect. Which ends once they are
			// equal. We use interpolation for smoother transition no matter
			// what the rendering frame rate is.
			float t = Math.max(0f, 1f - (currentTime - mTimeLast) * .005f);
			mTouchPositions[2] = mTouchPositions[0]
					+ (mTouchPositions[2] - mTouchPositions[0]) * t;
			mTouchPositions[3] = mTouchPositions[1]
					+ (mTouchPositions[3] - mTouchPositions[1]) * t;
		}

		// Store current time.
		mTimeLast = currentTime;

		// If we're out of tick timer bounds.
		if (currentTime - mTimeTickStart > ANIMATION_TICK_TIME
				|| mTimeTickStart < 0) {
			mTimeTickStart = currentTime;
			newTime = true;
		}

		// Calculate time interpolator, a value between [0, 1].
		float timeT = (currentTime - mTimeTickStart)
				/ (float) ANIMATION_TICK_TIME;

		// Disable unneeded rendering flags.
		GLES20.glDisable(GLES20.GL_CULL_FACE);
		GLES20.glDisable(GLES20.GL_BLEND);
		GLES20.glDisable(GLES20.GL_DEPTH_TEST);

		// Render scene to offscreen FBOs.
		mLBFbo.bind();
		// Render background.
		mLBFbo.bindTexture(0);
		mRendererBg.onDrawFrame(timeT, newTime);
		// Render foreground.
		mLBFbo.bindTexture(1);
		GLES20.glClearColor(0, 0, 0, 0);
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
		mRendererFg.onDrawFrame(mScreenVertices, timeT, newTime);

		// Copy FBOs to screen buffer.
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

		// Enable final copy shader.
		mShaderCopy.useProgram();
		int sTextureBg = mShaderCopy.getHandle("sTextureBg");
		int sTextureFg = mShaderCopy.getHandle("sTextureFg");
		int uTouchPos = mShaderCopy.getHandle("uTouchPos");
		int uRandom = mShaderCopy.getHandle("uRandom");
		int aPosition = mShaderCopy.getHandle("aPosition");

		// Set touch coordinates for shader.
		GLES20.glUniform2fv(uTouchPos, 2, mTouchPositions, 0);
		// Pass pseudo random number generator seed to shader.
		GLES20.glUniform1f(uRandom, (float) Math.random() + 50f);
		// Enable vertex coordinate array.
		GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_BYTE, false, 0,
				mScreenVertices);
		GLES20.glEnableVertexAttribArray(aPosition);

		// Set up fore- and background textures.
		GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mLBFbo.getTexture(0));
		GLES20.glUniform1i(sTextureBg, 0);
		GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mLBFbo.getTexture(1));
		GLES20.glUniform1i(sTextureFg, 1);

		// Render scene to screen buffer.
		GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
	}

	@Override
	public void onSurfaceChanged(GL10 unused, int width, int height) {
		// If shader compiler is not supported set viewport size only.
		if (mShaderCompilerSupported[0] == false) {
			GLES20.glViewport(0, 0, width, height);
			return;
		}

		// Store surface width and height for later use.
		mWidth = width;
		mHeight = height;

		// Set viewport size.
		GLES20.glViewport(0, 0, mWidth, mHeight);
		// Initialize two fbo textures.
		mLBFbo.init(mWidth, mHeight, 2);

		// Bind bacground texture and clear it. This is the only time we do
		// this, later on it'll be only overdrawn with background renderer.
		mLBFbo.bind();
		mLBFbo.bindTexture(0);
		GLES20.glClearColor(0, 0, 0, 1);
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

		// Notify foreground renderer surface has been changed.
		mRendererFg.onSurfaceChanged(mWidth, mHeight);
	}

	@Override
	public void onSurfaceCreated(GL10 unused, EGLConfig config) {
		// Check if shader compiler is supported.
		GLES20.glGetBooleanv(GLES20.GL_SHADER_COMPILER,
				mShaderCompilerSupported, 0);

		// If not, show user an error message and return immediately.
		if (mShaderCompilerSupported[0] == false) {
			Handler handler = new Handler(mContext.getMainLooper());
			handler.post(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(mContext, R.string.error_shader_compiler,
							Toast.LENGTH_LONG).show();
				}
			});
			return;
		}

		// Initiate copy shader.
		mShaderCopy.setProgram(mContext.getString(R.string.shader_copy_vs),
				mContext.getString(R.string.shader_copy_fs));

		// Initialize back- and foreground renderers.
		mRendererBg.onSurfaceCreated(mContext);
		mRendererFg.onSurfaceCreated(mContext);
	}

	/**
	 * Touch event callback method.
	 * 
	 * @param me
	 *            Current motion/touch event.
	 */
	public void onTouchEvent(MotionEvent me) {
		switch (me.getAction()) {
		// On touch down set following flag and initialize touch position start
		// and current values.
		case MotionEvent.ACTION_DOWN:
			mTouchFollow = true;
			mTouchPositions[0] = mTouchPositions[2] = me.getX() / mWidth;
			mTouchPositions[1] = mTouchPositions[3] = 1f - (me.getY() / mHeight);
			break;
		// On touch move update current position only.
		case MotionEvent.ACTION_MOVE:
			mTouchPositions[2] = me.getX() / mWidth;
			mTouchPositions[3] = 1f - (me.getY() / mHeight);
			break;
		// On touch up mark touch follow flag as false.
		case MotionEvent.ACTION_UP:
			mTouchFollow = false;
			break;
		}
	}

}
