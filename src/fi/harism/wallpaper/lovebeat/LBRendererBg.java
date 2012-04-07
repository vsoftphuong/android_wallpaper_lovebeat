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
		genRandAnimation();
	}

	private void genRandAnimation() {

		// TODO: Add new patterns.

		mFillDataCount = 2;
		StructFillData fillData0 = mFillData[0];
		StructFillData fillData1 = mFillData[1];

		int i = (int) (Math.random() * 3);
		switch (i) {
		case 0:
			genRandFill(fillData0, -1, 0, 1, 0, 0, 1);
			genRandFill(fillData1, -1, 0, 1, 0, 0, -1);
			break;
		case 1:
			genRandFill(fillData0, -1, 1, 1, 1, 1, -1);
			genRandFill(fillData1, 1, -1, -1, -1, -1, 1);
			break;
		default:
			genRandFill(fillData0, -1, 1, 1, -1, 1, 1);
			genRandFill(fillData1, -1, 1, 1, -1, -1, -1);
			break;
		}

	}

	private void genRandFill(StructFillData fillData, float x1, float y1,
			float x2, float y2, float nx, float ny) {
		// Select random color.
		fillData.mColorIndex = (int) (Math.random() * mBgColors.length);
		// Set normal.
		fillData.mFillNormal[0] = nx;
		fillData.mFillNormal[1] = ny;
		// Set fill source and target positions.
		int i = Math.random() > 0.5 ? 2 : 0;
		fillData.mFillPositions[i + 0] = x1;
		fillData.mFillPositions[i + 1] = y1;
		i = (i + 2) % 4;
		fillData.mFillPositions[i + 0] = x2;
		fillData.mFillPositions[i + 1] = y2;
	}

	public void onDrawFrame(ByteBuffer screenVertices, float timeT) {

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
			genRandAnimation();
		}

	}

	public void onSurfaceChanged() {
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
