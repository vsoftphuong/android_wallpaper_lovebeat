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

import android.content.Context;
import android.graphics.Color;
import android.opengl.GLES20;

public final class LBRendererFg {

	private static final String BOX_COLORS[] = { "#E4E3E4", "#CCE2CE",
			"#B2DDB3", "#92C680", "#7FB048", "#DCE3B7", "#CDDF91", "#B1CE60",
			"#8EB739", "#D5E4CA", "#B1E2B7", "#78C296", "#4D946E" };

	private double mAngleUp;
	private int mAngleUpCounter;
	private double mAngleUpTarget;

	// Render area aspect ratio.
	private final float mAspectRatio[] = new float[2];

	private float mBoxColors[][];

	private final StructBox mBoxes[] = new StructBox[8];
	private int mLoveBeat = 0;
	// Shader for rendering filled foreground boxes.
	private final LBShader mShaderFg = new LBShader();

	public LBRendererFg() {
		// Initialize box struct array.
		for (int i = 0; i < mBoxes.length; ++i) {
			mBoxes[i] = new StructBox();
		}
		// Convert string color values into floating point ones.
		mBoxColors = new float[BOX_COLORS.length][];
		for (int i = 0; i < BOX_COLORS.length; ++i) {
			// Parse color string into integer.
			int color = Color.parseColor(BOX_COLORS[i]);
			// Calculate float values.
			mBoxColors[i] = new float[3];
			mBoxColors[i][0] = Color.red(color) / 255f;
			mBoxColors[i][1] = Color.green(color) / 255f;
			mBoxColors[i][2] = Color.blue(color) / 255f;
		}
	}

	private float mix(float x1, float x2, float t) {
		return x1 + (x2 - x1) * t;
	}

	public void onDrawFrame(ByteBuffer screenVertices, float timeT,
			boolean newTime) {
		// Smooth Hermite interpolator.
		float hermiteT = timeT * timeT * (3 - 2 * timeT);
		// If we have new time span.
		if (newTime) {
			// Increase the beat.
			++mLoveBeat;
			// Decrease angle wait counter and generate new up direction once it
			// goes negative.
			if (--mAngleUpCounter < 0) {
				// Generate random up vector direction index.
				int dirIdx = (int) (Math.random() * 4);
				// Direction = dir * (PI * 2 / 8).
				mAngleUpTarget = (dirIdx * Math.PI * 2.0) / 8.0;
				// Generate random waiting time for rotation.
				mAngleUpCounter = (int) (Math.random() * 5) + 3;
			} else {
				// Store target for later use.
				mAngleUp = mAngleUpTarget;
			}
		}

		// Calculate final up vector value for rendering.
		double angle = mAngleUp + (mAngleUpTarget - mAngleUp) * hermiteT;
		// Up direction for x and y.
		float upX = (float) Math.sin(angle) * mAspectRatio[0];
		float upY = (float) Math.cos(angle) * mAspectRatio[1];

		// Initialize foreground shader for use.
		mShaderFg.useProgram();
		int uAspectRatio = mShaderFg.getHandle("uAspectRatio");
		int uCenterPos = mShaderFg.getHandle("uCenterPos");
		int uVectorUp = mShaderFg.getHandle("uVectorUp");
		int uScale = mShaderFg.getHandle("uScale");
		int uColor = mShaderFg.getHandle("uColor");
		int aPosition = mShaderFg.getHandle("aPosition");

		GLES20.glUniform2fv(uAspectRatio, 1, mAspectRatio, 0);
		GLES20.glUniform2f(uVectorUp, upX, upY);
		// Initiate vertex buffer.
		GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_BYTE, false, 0,
				screenVertices);
		GLES20.glEnableVertexAttribArray(aPosition);

		for (StructBox box : mBoxes) {
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
					box.mColorIdx = (int) (Math.random() * mBoxColors.length);
				} else {
					// box.mPaused = true;
				}
			}

			float t = box.mPaused ? 1f : hermiteT;
			float scale = mix(box.mScaleSource, box.mScaleTarget, t);
			float x = mix(box.mPosSource[0], box.mPosTarget[0], t);
			float y = mix(box.mPosSource[1], box.mPosTarget[1], t);

			GLES20.glUniform1f(uScale, scale);
			GLES20.glUniform2f(uCenterPos, x, y);
			// TODO: Implement colors.
			if (mLoveBeat > 10 && Math.random() > 0.2) {
				mLoveBeat = 0;
				GLES20.glUniform3f(uColor, .5f, .2f, .15f);
			} else {
				GLES20.glUniform3fv(uColor, 1, mBoxColors[box.mColorIdx], 0);
			}
			GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
		}
	}

	public void onSurfaceChanged(int width, int height) {
		mAspectRatio[0] = Math.max(width, height) / (float) width;
		mAspectRatio[1] = Math.max(width, height) / (float) height;
	}

	public void onSurfaceCreated(Context ctx) {
		// Initialize foreground shader.
		mShaderFg.setProgram(ctx.getString(R.string.shader_foreground_vs),
				ctx.getString(R.string.shader_foreground_fs));
	}

	private final class StructBox {
		public int mColorIdx;
		public boolean mPaused = true;
		public final float mPosSource[] = new float[2];
		public final float mPosTarget[] = new float[2];
		public float mScaleSource, mScaleTarget;
	}

}
