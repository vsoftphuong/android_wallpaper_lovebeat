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

/**
 * Background renderer class.
 */
public final class LBRendererBg {

	// Background color array. Colors are supposed to be hex decimals "#RRGGBB".
	private static final String[] BG_COLORS = { "#181718", "#181718",
			"#353335", "#447718" };
	// Static color values converted to floats [0.0, 1.0].
	private final float[][] mBgColors = new float[BG_COLORS.length][0];
	// Static coordinate buffer for rendering background.
	private ByteBuffer mFillBuffer;
	// Fill data elements array.
	private StructFillData mFillData[] = new StructFillData[4];
	// Number of fill data elements for rendering.
	private int mFillDataCount;
	// Last time interpolator.
	public float mLastTimeT = 0;
	// Shader for rendering filled background area.
	private final LBShader mShaderBg = new LBShader();

	/**
	 * Default constructor.
	 */
	public LBRendererBg() {
		// Generate fill vertex array coordinates. Coordinates are given as
		// tuples {targetT,normalT}. Where targetT=0 for sourcePos, targetT=1
		// for targetPos. And final coordinate is position+(normal*normalT).
		final byte[] FILL_COORDS = { 0, 0, 0, 1, 1, 0, 1, 1 };
		mFillBuffer = ByteBuffer.allocateDirect(8);
		mFillBuffer.put(FILL_COORDS).position(0);

		// Convert string color values into floating point ones.
		for (int i = 0; i < BG_COLORS.length; ++i) {
			// Parse color string into integer.
			int color = Color.parseColor(BG_COLORS[i]);
			// Calculate float values.
			mBgColors[i] = new float[3];
			mBgColors[i][0] = Color.red(color) / 255f;
			mBgColors[i][1] = Color.green(color) / 255f;
			mBgColors[i][2] = Color.blue(color) / 255f;
		}

		// Instantiate fill data array.
		for (int i = 0; i < mFillData.length; ++i) {
			mFillData[i] = new StructFillData();
		}
		// Generate first animation.
		genRandFillData();
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
	private void genFillData(float x1, float y1, float x2, float y2, float nx,
			float ny) {
		// Select random color from predefined colors array.
		int colorIndex = (int) (Math.random() * mBgColors.length);
		// Randomly split filling in two independent fill areas.
		int fillDataCount = Math.random() > 0.8 ? 2 : 1;
		// Generate fill struct data.
		for (int curIdx = 0; curIdx < fillDataCount; ++curIdx) {
			// Take next unused StructFillData.
			StructFillData fillData = mFillData[mFillDataCount++];
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
	private void genRandFillData() {
		// First reset fill data counter. Do note that genFillData increases
		// this counter once called.
		mFillDataCount = 0;

		// Select random integer for selecting animation.
		int i = (int) (Math.random() * 5);
		// TODO: Add comments for case clauses.
		switch (i) {
		case 0:
			genFillData(-1, 0, 1, 0, 0, 1);
			genFillData(-1, 0, 1, 0, 0, -1);
			break;
		case 1:
			genFillData(-1, 1, -1, 0, 2, 0);
			genFillData(-1, -1, -1, 0, 2, 0);
			break;
		case 2:
			genFillData(-1, 1, 1, 1, 1, -1);
			genFillData(1, -1, -1, -1, -1, 1);
			break;
		case 3:
			genFillData(-1, 1, 1, 1, -1, -1);
			genFillData(1, -1, -1, -1, 1, 1);
			break;
		default:
			genFillData(-1, 1, 1, -1, 1, 1);
			genFillData(-1, 1, 1, -1, -1, -1);
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
	public void onDrawFrame(float timeT, boolean newTime) {
		// Smooth Hermite interpolation.
		timeT = timeT * timeT * (3 - 2 * timeT);
		// Calculate source and target interpolant t values.
		float sourceT = mLastTimeT;
		float targetT = newTime ? 1 : timeT;

		// Initialize background shader for use.
		mShaderBg.useProgram();
		int uInterpolators = mShaderBg.getHandle("uInterpolators");
		int uPositions = mShaderBg.getHandle("uPositions");
		int uNormal = mShaderBg.getHandle("uNormal");
		int uColor = mShaderBg.getHandle("uColor");
		int aPosition = mShaderBg.getHandle("aPosition");

		// Store interpolants.
		GLES20.glUniform2f(uInterpolators, sourceT, targetT);
		// Initiate vertex buffer.
		GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_BYTE, false, 0,
				mFillBuffer);
		GLES20.glEnableVertexAttribArray(aPosition);

		// Iterate over active fill data structs.
		for (int i = 0; i < mFillDataCount; ++i) {
			// Grab local reference for fill data.
			StructFillData fillData = mFillData[i];
			// Store fill data position and normal into shader.
			GLES20.glUniform2fv(uPositions, 2, fillData.mFillPositions, 0);
			GLES20.glUniform2fv(uNormal, 1, fillData.mFillNormal, 0);
			// Store fill data color into shader.
			GLES20.glUniform3fv(uColor, 1, mBgColors[fillData.mColorIndex], 0);
			// Render fill area.
			GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
		}

		// Finally update mLastTime and generate new animation if needed.
		if (newTime) {
			mLastTimeT = 0;
			// Probability for generating new animation.
			if (Math.random() > 0.3) {
				genRandFillData();
			}
		} else {
			mLastTimeT = timeT;
		}
	}

	/**
	 * Called from main renderer once surface has been created.
	 * 
	 * @param ctx
	 *            Current application context.
	 */
	public void onSurfaceCreated(Context ctx) {
		// Initialize background shader.
		mShaderBg.setProgram(ctx.getString(R.string.shader_background_vs),
				ctx.getString(R.string.shader_background_fs));
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
