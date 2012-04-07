package fi.harism.wallpaper.lovebeat;

import java.nio.ByteBuffer;

import android.content.Context;
import android.opengl.GLES20;

public final class LBRendererBg {

	// Shader for rendering background gradient.
	private final LBShader mShaderBg = new LBShader();

	private final byte[][] BG_COLORS = { { 0x35, 0x33, 0x35 },
			{ 0x44, 0x77, 0x18 } };
	private float[][] mBgColors;

	public LBRendererBg() {
		mBgColors = new float[BG_COLORS.length][];
		for (int i = 0; i < BG_COLORS.length; ++i) {
			mBgColors[i] = new float[3];
			for (int j = 0; j < 3; ++j) {
				mBgColors[i][j] = BG_COLORS[i][j] / 255f;
			}
		}
	}

	public void onDrawFrame(ByteBuffer screenVertices) {
		mShaderBg.useProgram();
		int uColor1 = mShaderBg.getHandle("uBgColor1");
		int uColor2 = mShaderBg.getHandle("uBgColor2");
		int aPosition = mShaderBg.getHandle("aPosition");

		GLES20.glUniform3fv(uColor1, 1, mBgColors[0], 0);
		GLES20.glUniform3fv(uColor2, 1, mBgColors[1], 0);

		GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_BYTE, false, 0,
				screenVertices);
		GLES20.glEnableVertexAttribArray(aPosition);

		GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
	}

	public void onSurfaceChanged() {

	}

	public void onSurfaceCreated(Context ctx) {
		mShaderBg.setProgram(ctx.getString(R.string.shader_background_vs),
				ctx.getString(R.string.shader_background_fs));
	}

}
