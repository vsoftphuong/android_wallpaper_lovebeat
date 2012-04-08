package fi.harism.wallpaper.lovebeat;

import java.nio.ByteBuffer;

import android.content.Context;
import android.graphics.Color;
import android.opengl.GLES20;

public final class LBRendererBg {

	// Background color array. Colors are supposed to be hex decimals "#RRGGBB".
	private final String[] BG_COLORS = { "#181718", "#181718", "#353335",
			"#447718" };
	// Static color values converted to floats [0.0, 1.0].
	private float[][] mBgColors;

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

	public LBRendererBg() {
		final byte[] FILL_COORDS = { 0, 0, 0, 1, 1, 0, 1, 1 };
		mFillBuffer = ByteBuffer.allocateDirect(8);
		mFillBuffer.put(FILL_COORDS).position(0);

		mBgColors = new float[BG_COLORS.length][];
		for (int i = 0; i < BG_COLORS.length; ++i) {
			int color = Color.parseColor(BG_COLORS[i]);
			mBgColors[i] = new float[3];
			mBgColors[i][0] = Color.red(color) / 255f;
			mBgColors[i][1] = Color.green(color) / 255f;
			mBgColors[i][2] = Color.blue(color) / 255f;
		}

		for (int i = 0; i < mFillData.length; ++i) {
			mFillData[i] = new StructFillData();
		}
		genRandFillData();
	}

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

	private void genRandFillData() {
		mFillDataCount = 0;

		int i = (int) (Math.random() * 16);
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
		case 4:
			genFillData(-1, 1, 1, -1, 1, 1);
			genFillData(-1, 1, 1, -1, -1, -1);
			break;
		}
	}

	public void onDrawFrame(float timeT) {
		// Smooth Hermite interpolation.
		timeT = timeT * timeT * (3 - 2 * timeT);
		float startT = mLastTimeT;
		float endT = timeT >= startT ? timeT : 1;

		mShaderBg.useProgram();
		int uInterpolators = mShaderBg.getHandle("uInterpolators");
		int uPositions = mShaderBg.getHandle("uPositions");
		int uNormal = mShaderBg.getHandle("uNormal");
		int uColor = mShaderBg.getHandle("uColor");
		int aPosition = mShaderBg.getHandle("aPosition");

		GLES20.glUniform2f(uInterpolators, startT, endT);
		GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_BYTE, false, 0,
				mFillBuffer);
		GLES20.glEnableVertexAttribArray(aPosition);

		for (int ii = 0; ii < mFillDataCount; ++ii) {
			StructFillData fillData = mFillData[ii];

			GLES20.glUniform2fv(uPositions, 2, fillData.mFillPositions, 0);
			GLES20.glUniform2fv(uNormal, 1, fillData.mFillNormal, 0);

			GLES20.glUniform3fv(uColor, 1, mBgColors[fillData.mColorIndex], 0);
			GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
		}

		if (timeT >= mLastTimeT) {
			mLastTimeT = timeT;
		} else {
			mLastTimeT = 0;
			genRandFillData();
		}

	}

	public void onSurfaceCreated(Context ctx) {
		mShaderBg.setProgram(ctx.getString(R.string.shader_background_vs),
				ctx.getString(R.string.shader_background_fs));
	}

	private class StructFillData {
		public int mColorIndex;
		public float mFillNormal[] = new float[2];
		public float mFillPositions[] = new float[4];
	}

}
