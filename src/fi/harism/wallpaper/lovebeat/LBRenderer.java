/*
   Copyright 2012 Harri Smått

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
import android.widget.Toast;

/**
 * Main renderer class.
 */
public final class LBRenderer implements GLSurfaceView.Renderer {

	// Current context.
	private Context mContext;
	// FBO for offscreen rendering.
	private LBFbo mLBFbo = new LBFbo();
	// Vertex buffer for full scene coordinates.
	private ByteBuffer mScreenVertices;
	// Shader for rendering background gradient.
	private final LBShader mShaderBackground = new LBShader();
	// Flag for indicating whether shader compiler is supported.
	private final boolean[] mShaderCompilerSupported = new boolean[1];
	// Shader for copying offscreen texture on screen.
	private final LBShader mShaderCopy = new LBShader();
	// Surface/screen dimensions.
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

		// Disable unneeded rendering flags.
		GLES20.glDisable(GLES20.GL_CULL_FACE);
		GLES20.glDisable(GLES20.GL_BLEND);
		GLES20.glDisable(GLES20.GL_DEPTH_TEST);

		// Set render target to fbo.
		mLBFbo.bind();
		mLBFbo.bindTexture(0);

		// Render background gradient.
		mShaderBackground.useProgram();
		int aPosition = mShaderBackground.getHandle("aPosition");

		GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_BYTE, false, 0,
				mScreenVertices);
		GLES20.glEnableVertexAttribArray(aPosition);

		GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

		// TODO: Render scene.

		// Copy FBO to screen buffer.
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
		GLES20.glViewport(0, 0, mWidth, mHeight);
		mShaderCopy.useProgram();
		aPosition = mShaderCopy.getHandle("aPosition");
		GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_BYTE, false, 0,
				mScreenVertices);
		GLES20.glEnableVertexAttribArray(aPosition);
		GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mLBFbo.getTexture(0));
		GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
	}

	@Override
	public void onSurfaceChanged(GL10 unused, int width, int height) {
		// If shader compiler is not supported set viewport size only.
		if (mShaderCompilerSupported[0] == false) {
			GLES20.glViewport(0, 0, width, height);
			return;
		}

		mWidth = width;
		mHeight = height;
		mLBFbo.init(mWidth, mHeight, 1);
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

		mShaderCopy.setProgram(mContext.getString(R.string.shader_copy_vs),
				mContext.getString(R.string.shader_copy_fs));
		mShaderBackground.setProgram(
				mContext.getString(R.string.shader_background_vs),
				mContext.getString(R.string.shader_background_fs));
	}

}
