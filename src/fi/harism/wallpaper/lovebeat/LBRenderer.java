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
import android.graphics.Color;
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

	// Background color array. Colors are supposed to be hex decimals "#RRGGBB".
	private static final String[] BG_COLORS = { "#636063", "#636063",
			"#535053", "#447718" };
	// Foreground color array. Colors are supposed to be hex decimals "#RRGGBB".
	private static final String FG_COLORS[] = { "#E4E3E4", "#CCE2CE",
			"#B2DDB3", "#92C680", "#7FB048", "#DCE3B7", "#CDDF91", "#B1CE60",
			"#8EB739", "#D5E4CA", "#B1E2B7", "#78C296", "#4D946E" };

	/**
	 * Background variables.
	 */

	// Static color values converted to floats [0.0, 1.0].
	private final float[][] bg_Colors = new float[BG_COLORS.length][];
	// Static coordinate buffer for rendering background.
	private ByteBuffer bg_FillBuffer;
	// Fill data elements array.
	private StructFillData bg_FillData[] = new StructFillData[4];
	// Number of fill data elements for rendering.
	private int bg_FillDataCount;
	// Last time interpolator.
	public float bg_LastTimeT = 0;
	// Shader for rendering filled background area.
	private final LBShader bg_Shader = new LBShader();

	/**
	 * Foreground variables.
	 */

	private double fg_AngleUp;
	private int fg_AngleUpCounter;
	private double fg_AngleUpTarget;
	private final StructBoxData fg_Boxes[] = new StructBoxData[8];
	private final float fg_Colors[][] = new float[FG_COLORS.length][];
	private int fg_LoveBeat = 0;
	// Shader for rendering filled foreground boxes.
	private final LBShader fg_Shader = new LBShader();

	/**
	 * Common variables.
	 */

	// Render area aspect ratio.
	private final float mAspectRatio[] = new float[2];
	// Application context.
	private Context mContext;
	// FBOs for offscreen rendering.
	private LBFbo mFbo = new LBFbo();
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
	// Surface width and height;
	private int mWidth, mHeight;

	/**
	 * Default constructor.
	 */
	public LBRenderer(Context context) {

		/**
		 * Instantiate common variables.
		 */

		// Store application context for later use.
		mContext = context;

		// Create screen coordinates buffer.
		final byte SCREEN_COORDS[] = { -1, 1, -1, -1, 1, 1, 1, -1 };
		mScreenVertices = ByteBuffer.allocateDirect(2 * 4);
		mScreenVertices.put(SCREEN_COORDS).position(0);

		/**
		 * Instantiate background rendering variables.
		 */

		// Generate fill vertex array coordinates. Coordinates are given as
		// tuples {targetT,normalT}. Where targetT=0 for sourcePos, targetT=1
		// for targetPos. And final coordinate is position+(normal*normalT).
		final byte[] FILL_COORDS = { 0, 0, 0, 1, 1, 0, 1, 1 };
		bg_FillBuffer = ByteBuffer.allocateDirect(8);
		bg_FillBuffer.put(FILL_COORDS).position(0);

		// Convert string color values into floating point ones.
		for (int i = 0; i < BG_COLORS.length; ++i) {
			// Parse color string into integer.
			int color = Color.parseColor(BG_COLORS[i]);
			// Calculate float values.
			bg_Colors[i] = new float[3];
			bg_Colors[i][0] = Color.red(color) / 255f;
			bg_Colors[i][1] = Color.green(color) / 255f;
			bg_Colors[i][2] = Color.blue(color) / 255f;
		}

		// Instantiate fill data array.
		for (int i = 0; i < bg_FillData.length; ++i) {
			bg_FillData[i] = new StructFillData();
		}
		// Generate first animation.
		bg_GenRandFillData();

		/**
		 * Instantiate foreground rendering variables.
		 */

		// Initialize box struct array.
		for (int i = 0; i < fg_Boxes.length; ++i) {
			fg_Boxes[i] = new StructBoxData();
		}
		// Convert string color values into floating point ones.
		for (int i = 0; i < FG_COLORS.length; ++i) {
			// Parse color string into integer.
			int color = Color.parseColor(FG_COLORS[i]);
			// Calculate float values.
			fg_Colors[i] = new float[3];
			fg_Colors[i][0] = Color.red(color) / 255f;
			fg_Colors[i][1] = Color.green(color) / 255f;
			fg_Colors[i][2] = Color.blue(color) / 255f;
		}
	}

	/**
	 * Generates/stores given points and normal into fill data array. Fill areas
	 * are presented by three variables; source point, target point and normal.
	 * In some cases, using random number generator, given area is split into
	 * two. Also, similarly, source and target positions are swapped for some
	 * random behavior in order to make effect more lively.
	 * 
	 * @param x1
	 *            Source position x.
	 * @param y1
	 *            Source position y.
	 * @param x2
	 *            Target position x.
	 * @param y2
	 *            Target position y.
	 * @param nx
	 *            Normal x.
	 * @param ny
	 *            Normal y.
	 */
	private void bg_GenFillData(float x1, float y1, float x2, float y2,
			float nx, float ny) {
		// Select random color from predefined colors array.
		int colorIndex = (int) (Math.random() * bg_Colors.length);
		// Randomly split filling in two independent fill areas.
		int fillDataCount = Math.random() > 0.8 ? 2 : 1;
		// Generate fill struct data.
		for (int curIdx = 0; curIdx < fillDataCount; ++curIdx) {
			// Take next unused StructFillData.
			StructFillData fillData = bg_FillData[bg_FillDataCount++];
			// Set common values.
			fillData.mColorIndex = colorIndex;
			fillData.mFillNormal[0] = nx;
			fillData.mFillNormal[1] = ny;

			// Calculate start and end positions using interpolation.
			float sourceT = (float) curIdx / fillDataCount;
			float targetT = (float) (curIdx + 1) / fillDataCount;

			// Finally store fill source and target positions. Plus randomly
			// swap them with each other for "reverse" effect.
			int posIdx = Math.random() > 0.5 ? 2 : 0;
			// Calculate new positions using sourceT and targetT.
			fillData.mFillPositions[posIdx + 0] = x1 + (x2 - x1) * sourceT;
			fillData.mFillPositions[posIdx + 1] = y1 + (y2 - y1) * sourceT;
			// Recalculate posIdx so that 0 --> 2 or 2 --> 0.
			posIdx = (posIdx + 2) % 4;
			fillData.mFillPositions[posIdx + 0] = x1 + (x2 - x1) * targetT;
			fillData.mFillPositions[posIdx + 1] = y1 + (y2 - y1) * targetT;
		}
	}

	/**
	 * Generates new fill/animation structure.
	 */
	private void bg_GenRandFillData() {
		// First reset fill data counter. Do note that genFillData increases
		// this counter once called.
		bg_FillDataCount = 0;

		// Select random integer for selecting animation.
		int i = (int) (Math.random() * 10);
		// TODO: Add comments for case clauses.
		switch (i) {
		// Vertical and horizontal fills.
		case 0:
			bg_GenFillData(-1, 1, -1, -1, 2, 0);
			break;
		case 1:
			bg_GenFillData(-1, 1, 1, 1, 0, -2);
			break;
		case 2:
			bg_GenFillData(-1, 1, -1, 0, 2, 0);
			bg_GenFillData(-1, 0, -1, -1, 2, 0);
			break;
		case 3:
			bg_GenFillData(-1, 1, 1, 1, 0, -1);
			bg_GenFillData(-1, 0, 1, 0, 0, -1);
			break;
		// Diagonal fills.
		case 4:
			bg_GenFillData(-1, 1, 1, 1, 3, -3);
			bg_GenFillData(-1, 1, -1, -1, 3, -3);
			break;
		case 5:
			bg_GenFillData(1, 1, -1, 1, -3, -3);
			bg_GenFillData(1, 1, 1, -1, -3, -3);
			break;
		case 6:
			bg_GenFillData(-1, -1, 1, 1, -1.5f, 1.5f);
			bg_GenFillData(-1, -1, 1, 1, 1.5f, -1.5f);
			break;
		case 7:
			bg_GenFillData(-1, 1, 1, -1, 1.5f, 1.5f);
			bg_GenFillData(-1, 1, 1, -1, -1.5f, -1.5f);
			break;
		case 8:
			bg_GenFillData(-2, 0, 0, 0, 1, 1);
			bg_GenFillData(2, 0, 0, 0, -1, -1);
			break;
		case 9:
			bg_GenFillData(2, 0, 0, 0, -1, 1);
			bg_GenFillData(-2, 0, 0, 0, 1, -1);
			break;
		}
	}

	/**
	 * Renders background onto current frame buffer.
	 * 
	 * @param timeT
	 *            Time interpolator, float between [0f, 1f].
	 * @param newTime
	 *            True once new [0f, 1f] timeT range is started.
	 */
	public void bg_OnDrawFrame(float timeT, boolean newTime) {
		// Smooth Hermite interpolation.
		timeT = timeT * timeT * (3 - 2 * timeT);
		// Calculate source and target interpolant t values.
		float sourceT = bg_LastTimeT;
		float targetT = newTime ? 1 : timeT;

		// Initialize background shader for use.
		bg_Shader.useProgram();
		int uInterpolators = bg_Shader.getHandle("uInterpolators");
		int uPositions = bg_Shader.getHandle("uPositions");
		int uNormal = bg_Shader.getHandle("uNormal");
		int uColor = bg_Shader.getHandle("uColor");
		int aPosition = bg_Shader.getHandle("aPosition");

		// Store interpolants.
		GLES20.glUniform2f(uInterpolators, sourceT, targetT);
		// Initiate vertex buffer.
		GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_BYTE, false, 0,
				bg_FillBuffer);
		GLES20.glEnableVertexAttribArray(aPosition);

		// Iterate over active fill data structs.
		for (int i = 0; i < bg_FillDataCount; ++i) {
			// Grab local reference for fill data.
			StructFillData fillData = bg_FillData[i];
			// Store fill data position and normal into shader.
			GLES20.glUniform2fv(uPositions, 2, fillData.mFillPositions, 0);
			GLES20.glUniform2fv(uNormal, 1, fillData.mFillNormal, 0);
			// Store fill data color into shader.
			GLES20.glUniform3fv(uColor, 1, bg_Colors[fillData.mColorIndex], 0);
			// Render fill area.
			GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
		}

		// Finally update mLastTime and generate new animation if needed.
		if (newTime) {
			bg_LastTimeT = 0;
			// Probability for generating new animation.
			if (Math.random() < 0.3) {
				bg_GenRandFillData();
			}
		} else {
			bg_LastTimeT = timeT;
		}
	}

	public void fg_OnDrawFrame(float timeT, boolean newTime) {
		// Smooth Hermite interpolator.
		float hermiteT = timeT * timeT * (3 - 2 * timeT);
		// If we have new time span.
		if (newTime) {
			// Increase the beat.
			++fg_LoveBeat;
			// Decrease angle wait counter and generate new up direction once it
			// goes negative.
			if (--fg_AngleUpCounter < 0) {
				// Generate random up vector direction index.
				int dirIdx = (int) (Math.random() * 4);
				// Direction = dir * (PI * 2 / 8).
				fg_AngleUpTarget = (dirIdx * Math.PI * 2.0) / 8.0;
				// Generate random waiting time for rotation.
				fg_AngleUpCounter = (int) (Math.random() * 5) + 3;
			} else {
				// Store target for later use.
				fg_AngleUp = fg_AngleUpTarget;
			}
		}

		// Calculate final up vector value for rendering.
		double angle = fg_AngleUp + (fg_AngleUpTarget - fg_AngleUp) * hermiteT;
		// Up direction for x and y.
		float upX = (float) Math.sin(angle) * mAspectRatio[0];
		float upY = (float) Math.cos(angle) * mAspectRatio[1];

		// Initialize foreground shader for use.
		fg_Shader.useProgram();
		int uAspectRatio = fg_Shader.getHandle("uAspectRatio");
		int uCenterPos = fg_Shader.getHandle("uCenterPos");
		int uVectorUp = fg_Shader.getHandle("uVectorUp");
		int uScale = fg_Shader.getHandle("uScale");
		int uColor = fg_Shader.getHandle("uColor");
		int aPosition = fg_Shader.getHandle("aPosition");

		GLES20.glUniform2fv(uAspectRatio, 1, mAspectRatio, 0);
		GLES20.glUniform2f(uVectorUp, upX, upY);
		// Initiate vertex buffer.
		GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_BYTE, false, 0,
				mScreenVertices);
		GLES20.glEnableVertexAttribArray(aPosition);

		for (StructBoxData box : fg_Boxes) {
			if (newTime) {
				box.mPosSource[0] = box.mPosTarget[0];
				box.mPosSource[1] = box.mPosTarget[1];
				box.mScaleSource = box.mScaleTarget;

				boolean paused = Math.random() > 0.1;
				if (!paused) {
					if (box.mPaused) {
						box.mPaused = false;
						box.mPosSource[0] = (float) ((Math.random() * 2.2) - 1.1);
						box.mPosSource[1] = (float) ((Math.random() * 2.2) - 1.1);
					}
					box.mPosTarget[0] = (float) ((Math.random() * 1.8) - 0.9);
					box.mPosTarget[1] = (float) ((Math.random() * 1.8) - 0.9);
					box.mPosTarget[0] = (Math.round(box.mPosTarget[0] * 10) / 10f);
					box.mPosTarget[1] = (Math.round(box.mPosTarget[1] * 10) / 10f);
					box.mScaleTarget = (float) ((Math.random() * 0.1) + 0.1);
					box.mColorIdx = (int) (Math.random() * fg_Colors.length);
				} else {
					// box.mPaused = true;
				}
			}

			float t = box.mPaused ? 1f : hermiteT;
			float scale = box.mScaleSource
					+ (box.mScaleTarget - box.mScaleSource) * t;
			float x = box.mPosSource[0]
					+ (box.mPosTarget[0] - box.mPosSource[0]) * t;
			float y = box.mPosSource[1]
					+ (box.mPosTarget[1] - box.mPosSource[1]) * t;

			GLES20.glUniform1f(uScale, scale);
			GLES20.glUniform2f(uCenterPos, x, y);
			// TODO: Implement colors.
			if (fg_LoveBeat > 10 && Math.random() > 0.2) {
				fg_LoveBeat = 0;
				GLES20.glUniform3f(uColor, .5f, .2f, .15f);
			} else {
				GLES20.glUniform3fv(uColor, 1, fg_Colors[box.mColorIdx], 0);
			}
			GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
		}
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
		final long ANIMATION_TICK_TIME = 2000;
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

		/**
		 * Render scene to offscreen FBOs.
		 */
		mFbo.bind();
		// Render background.
		mFbo.bindTexture(0);
		bg_OnDrawFrame(timeT, newTime);
		// Render foreground.
		mFbo.bindTexture(1);
		// Clear foreground fbo texture only.
		GLES20.glClearColor(0, 0, 0, 0);
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
		fg_OnDrawFrame(timeT, newTime);

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
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFbo.getTexture(0));
		GLES20.glUniform1i(sTextureBg, 0);
		GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFbo.getTexture(1));
		GLES20.glUniform1i(sTextureFg, 1);

		// Render scene to screen buffer.
		GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
	}

	@Override
	public void onSurfaceChanged(GL10 unused, int width, int height) {
		// Store width and height for later use.
		mWidth = width;
		mHeight = height;
		// Set viewport size.
		GLES20.glViewport(0, 0, mWidth, mHeight);
		// If shader compiler is not supported set viewport size only.
		if (mShaderCompilerSupported[0] == false) {
			return;
		}

		// Calculate aspect ratio.
		mAspectRatio[0] = Math.max(mWidth, mHeight) / (float) mWidth;
		mAspectRatio[1] = Math.max(mWidth, mHeight) / (float) mHeight;

		// Initialize two fbo screen sized textures.
		mFbo.init(mWidth, mHeight, 2);

		// Bind background texture and clear it. This is the only time we do
		// this, later on it'll be only overdrawn with background renderer.
		mFbo.bind();
		mFbo.bindTexture(0);
		GLES20.glClearColor(0, 0, 0, 1);
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
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

		// Initialize background shader.
		bg_Shader.setProgram(mContext.getString(R.string.shader_background_vs),
				mContext.getString(R.string.shader_background_fs));
		// Initialize foreground shader.
		fg_Shader.setProgram(mContext.getString(R.string.shader_foreground_vs),
				mContext.getString(R.string.shader_foreground_fs));
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

	/**
	 * Struct for storing box related data.
	 */
	private final class StructBoxData {
		public int mColorIdx;
		public boolean mPaused = true;
		public final float mPosSource[] = new float[2];
		public final float mPosTarget[] = new float[2];
		public float mScaleSource, mScaleTarget;
	}

	/**
	 * Private fill data structure for storing source position, target position,
	 * normal and color index. Normal is stored as {x,y} tuple and positions as
	 * two {x,y} tuples.
	 */
	private final class StructFillData {
		// Color index.
		public int mColorIndex;
		// Normal direction.
		public final float mFillNormal[] = new float[2];
		// Source and target positions.
		public final float mFillPositions[] = new float[4];
	}

}
