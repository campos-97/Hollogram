package lutec.tec.hologram.OPENGL;


import android.opengl.GLES20;
import android.opengl.GLES31;
import android.opengl.GLES32;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.renderscript.Matrix4f;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import lutec.tec.hologram.MainActivity;
import lutec.tec.hologram.R;
import lutec.tec.hologram.obj.Model;
import lutec.tec.hologram.obj.OBJ_Loader;
import lutec.tec.hologram.obj.ObjLoader;

import lutec.tec.hologram.OPENGL.ErrorHandler.ErrorType;

/**
 * Created by josea on 9/26/2017.
 */

public class MyGLRenderer implements GLSurfaceView.Renderer {

    /** Used for debug logs. */
    private static final String TAG = "LessonEightRenderer";

    /** References to other main objects. */
    private final MainActivity lessonEightActivity;
    private final ErrorHandler   errorHandler;

    /**
     * Store the model matrix. This matrix is used to move models from object
     * space (where each model can be thought of being located at the center of
     * the universe) to world space.
     */
    private final float[] modelMatrix = new float[16];

    /**
     * Store the view matrix. This can be thought of as our camera. This matrix
     * transforms world space to eye space; it positions things relative to our
     * eye.
     */
    private final float[] viewMatrix = new float[16];

    /**
     * Store the projection matrix. This is used to project the scene onto a 2D
     * viewport.
     */
    private final float[] projectionMatrix = new float[16];

    /**
     * Allocate storage for the final combined matrix. This will be passed into
     * the shader program.
     */
    private final float[] mvpMatrix = new float[16];

    /** Additional matrices. */
    private final float[] accumulatedRotation = new float[16];
    private final float[] currentRotation = new float[16];
    private final float[] lightModelMatrix = new float[16];
    private final float[] temporaryMatrix = new float[16];

    /** OpenGL handles to our program uniforms. */
    private int mvpMatrixUniform;
    private int mvMatrixUniform;
    private int lightPosUniform;

    /** OpenGL handles to our program attributes. */
    private int positionAttribute;
    private int normalAttribute;
    private int colorAttribute;

    /** Identifiers for our uniforms and attributes inside the shaders. */
    private static final String MVP_MATRIX_UNIFORM = "u_MVPMatrix";
    private static final String MV_MATRIX_UNIFORM = "u_MVMatrix";
    private static final String LIGHT_POSITION_UNIFORM = "u_LightPos";

    private static final String POSITION_ATTRIBUTE = "a_Position";
    private static final String NORMAL_ATTRIBUTE = "a_Normal";
    private static final String COLOR_ATTRIBUTE = "a_Color";

    /** Additional constants. */
    private static final int POSITION_DATA_SIZE_IN_ELEMENTS = 3;
    private static final int NORMAL_DATA_SIZE_IN_ELEMENTS = 3;
    private static final int COLOR_DATA_SIZE_IN_ELEMENTS = 4;

    private static final int BYTES_PER_FLOAT = 4;
    private static final int BYTES_PER_SHORT = 2;

    private static final int STRIDE = (POSITION_DATA_SIZE_IN_ELEMENTS + NORMAL_DATA_SIZE_IN_ELEMENTS + COLOR_DATA_SIZE_IN_ELEMENTS)
            * BYTES_PER_FLOAT;

    /**
     * Used to hold a light centered on the origin in model space. We need a 4th
     * coordinate so we can get translations to work when we multiply this by
     * our transformation matrices.
     */
    private final float[] lightPosInModelSpace = new float[] { 0.0f, 0.0f, 0.0f, 1.0f };

    /**
     * Used to hold the current position of the light in world space (after
     * transformation via model matrix).
     */
    private final float[] lightPosInWorldSpace = new float[4];

    /**
     * Used to hold the transformed position of the light in eye space (after
     * transformation via modelview matrix)
     */
    private final float[] lightPosInEyeSpace = new float[4];

    /** This is a handle to our cube shading program. */
    private int program;

    /** Retain the most recent delta for touch events. */
    // These still work without volatile, but refreshes are not guaranteed to
    // happen.
    public volatile float deltaX;
    public volatile float deltaY;

    /** The current heightmap object. */
    private Model model;


    private int mTextureUniformHandle;

    /** This will be used to pass in model texture coordinate information. */
    private int mTextureCoordinateHandle;


    /** These are handles to our texture data. */
    private int mAndroidDataHandle;

    /**
     * Initialize the model data.
     */
    public MyGLRenderer(final MainActivity lessonEightActivity, ErrorHandler errorHandler) {
        this.lessonEightActivity = lessonEightActivity;
        this.errorHandler = errorHandler;
    }

    @Override
    public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {


        model = new Model();
        try{
            InputStream file = lessonEightActivity.getAssets().open("cube3.obj");
            //final File dir = new File(mActivity.getFilesDir() +"src/main/assets");
            //dir.mkdir();
            model = OBJ_Loader.loadModel(file);
            model.loadVBO();
            Log.d("gg", "Vertex num: "+model.getVertices().capacity()+"  "+model.getNumObjectVertex());
        } catch (FileNotFoundException e){
            e.printStackTrace();
            System.exit(1);
        }catch (IOException e){
            e.printStackTrace();
            System.exit(1);
        }

        // Load the texture
        mAndroidDataHandle = TextureHelper.loadTexture(lessonEightActivity, R.drawable.lutec);
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mAndroidDataHandle);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mAndroidDataHandle);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);

        // Set the background clear color to black.
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        // Use culling to remove back faces.z
        //GLES20.glEnable(GLES20.GL_CULL_FACE);

        // Enable depth testing
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // Position the eye in front of the origin.
        final float eyeX = 0.0f;
        final float eyeY = 0.0f;
        final float eyeZ = -0.5f;

        // We are looking toward the distance
        final float lookX = 0.0f;
        final float lookY = 0.0f;
        final float lookZ = -5.0f;

        // Set our up vector. This is where our head would be pointing were we
        // holding the camera.
        final float upX = 0.0f;
        final float upY = 1.0f;
        final float upZ = 0.0f;

        // Set the view matrix. This matrix can be said to represent the camera
        // position.
        // NOTE: In OpenGL 1, a ModelView matrix is used, which is a combination
        // of a model and view matrix. In OpenGL 2, we can keep track of these
        // matrices separately if we choose.
        Matrix.setLookAtM(viewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, upX, upY, upZ);

        final String vertexShader = RawResourceReader.readTextFileFromRawResource(lessonEightActivity,
                R.raw.vertex_shader);
        final String fragmentShader = RawResourceReader.readTextFileFromRawResource(lessonEightActivity,
                R.raw.fragment_shader);

        final int vertexShaderHandle = ShaderHelper.compileShader(GLES20.GL_VERTEX_SHADER, vertexShader);
        final int fragmentShaderHandle = ShaderHelper.compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader);
/*
        program = ShaderHelper.createAndLinkProgram(vertexShaderHandle, fragmentShaderHandle, new String[] {
                POSITION_ATTRIBUTE, NORMAL_ATTRIBUTE, "a_TexCoordinate" });

                */

        program = ShaderHelper.createAndLinkProgram(vertexShaderHandle, fragmentShaderHandle, new String[] {
                POSITION_ATTRIBUTE, NORMAL_ATTRIBUTE, "a_TexCoordinate" });

        // Initialize the accumulated rotation matrix
        Matrix.setIdentityM(accumulatedRotation, 0);
    }

    int width1, height1;

    @Override
    public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        width1 = width;
        height1 = height;

        final float ratio = (float) width / height;
        final float left = -ratio;
        final float right = ratio;
        final float bottom = -1.0f;
        final float top = 1.0f;
        final float near = 1.0f;
        final float far = 1000.0f;

        Matrix.frustumM(projectionMatrix, 0, left, right, bottom, top, near, far);
    }

    @Override
    public void onDrawFrame(GL10 glUnused) {

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);

        GLES20.glViewport(0, 0, width1/2, height1/2);
        GLES20.glScissor(0, 0, width1/2, height1/2);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        renderScene();

        GLES20.glViewport(0, height1/2, width1/2, height1/2);
        GLES20.glScissor(0, height1/2, width1/2, height1/2);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        renderScene();

        GLES20.glViewport(width1/2, height1/4, width1/2, height1/2);
        GLES20.glScissor(width1/2, height1/4, width1/2, height1/2);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        renderScene();
    }

    private void renderScene(){
        // Set our per-vertex lighting program.
        GLES20.glUseProgram(program);

        // Set program handles for cube drawing.
        mvpMatrixUniform = GLES20.glGetUniformLocation(program, MVP_MATRIX_UNIFORM);
        mvMatrixUniform = GLES20.glGetUniformLocation(program, MV_MATRIX_UNIFORM);
        lightPosUniform = GLES20.glGetUniformLocation(program, LIGHT_POSITION_UNIFORM);
        positionAttribute = GLES20.glGetAttribLocation(program, POSITION_ATTRIBUTE);
        normalAttribute = GLES20.glGetAttribLocation(program, NORMAL_ATTRIBUTE);
        mTextureUniformHandle = GLES20.glGetUniformLocation(program, "u_Texture");
        mTextureCoordinateHandle = GLES20.glGetAttribLocation(program, "a_TexCoordinate");


        // Calculate position of the light. Push into the distance.
        Matrix.setIdentityM(lightModelMatrix, 0);
        Matrix.translateM(lightModelMatrix, 0, 0.0f,  0.0f, -1.0f);

        Matrix.multiplyMV(lightPosInWorldSpace, 0, lightModelMatrix, 0, lightPosInModelSpace, 0);
        Matrix.multiplyMV(lightPosInEyeSpace, 0, viewMatrix, 0, lightPosInWorldSpace, 0);

        // Draw the heightmap.
        // Translate the heightmap into the screen.
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, 0.0f, 0.0f, -3.5f);
        //Matrix.scaleM(modelMatrix,0,0.2f,0.2f,0.2f);

        //Matrix.scaleM(modelMatrix, 0, 10.0f, 10.0f, 10.0f);

        // Set a matrix that contains the current rotation.
        Matrix.setIdentityM(currentRotation, 0);
        Matrix.rotateM(currentRotation, 0, deltaX, 0.0f, 1.0f, 0.0f);
        Matrix.rotateM(currentRotation, 0, deltaY, 1.0f, 0.0f, 0.0f);
        deltaX = 0.0f;
        deltaY = 0.0f;


        // Multiply the current rotation by the accumulated rotation, and then
        // set the accumulated rotation to the result.
        Matrix.multiplyMM(temporaryMatrix, 0, currentRotation, 0, accumulatedRotation, 0);
        System.arraycopy(temporaryMatrix, 0, accumulatedRotation, 0, 16);

        // Rotate the cube taking the overall rotation into account.
        Matrix.multiplyMM(temporaryMatrix, 0, modelMatrix, 0, accumulatedRotation, 0);
        System.arraycopy(temporaryMatrix, 0, modelMatrix, 0, 16);

        // This multiplies the view matrix by the model matrix, and stores
        // the result in the MVP matrix
        // (which currently contains model * view).
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0);

        // Pass in the modelview matrix.
        GLES20.glUniformMatrix4fv(mvMatrixUniform, 1, false, mvpMatrix, 0);

        // This multiplies the modelview matrix by the projection matrix,
        // and stores the result in the MVP matrix
        // (which now contains model * view * projection).
        Matrix.multiplyMM(temporaryMatrix, 0, projectionMatrix, 0, mvpMatrix, 0);
        System.arraycopy(temporaryMatrix, 0, mvpMatrix, 0, 16);

        // Pass in the combined matrix.
        GLES20.glUniformMatrix4fv(mvpMatrixUniform, 1, false, mvpMatrix, 0);

        // Pass in the light position in eye space.
        GLES20.glUniform3f(lightPosUniform, lightPosInEyeSpace[0], lightPosInEyeSpace[1], lightPosInEyeSpace[2]);

        // activate texture 0, bind it, and pass to shader
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                mAndroidDataHandle);
        GLES20.glUniform1i(mTextureUniformHandle, 0);

        drawModel();


        /*
        GLES20.glVertexAttribPointer(positionAttribute, 3, GLES20.GL_FLOAT,
                false, 0, model.getVertices());
        GLES20.glVertexAttribPointer(normalAttribute, 3, GLES20.GL_FLOAT,
                false, 0, model.getNormals());
        GLES20.glVertexAttribPointer(mTextureCoordinateHandle, 2,
                GLES20.GL_FLOAT, false, 0, model.getTexCoords());

        GLES20.glEnableVertexAttribArray(positionAttribute);
        GLES20.glEnableVertexAttribArray(normalAttribute);
        GLES20.glEnableVertexAttribArray(mTextureCoordinateHandle);

        // draw the model
        GLES20.glDrawElements(GLES20.GL_TRIANGLES,
                model.getNumObjectIndex(), GLES20.GL_UNSIGNED_SHORT,
                model.getIndices());

        // disable the enabled arrays
        GLES20.glDisableVertexAttribArray(positionAttribute);
        GLES20.glDisableVertexAttribArray(normalAttribute);
        GLES20.glDisableVertexAttribArray(mTextureCoordinateHandle);

        */
    }

    private void drawModel(){
/*
        final int buffers[] = new int[3];
        GLES20.glGenBuffers(3, buffers, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, model.getVertices().capacity() * BYTES_PER_FLOAT, model.getVertices(), GLES20.GL_STATIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[1]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, model.getNormals().capacity() * BYTES_PER_FLOAT, model.getNormals(), GLES20.GL_STATIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[2]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, model.getTexCoords().capacity() * BYTES_PER_FLOAT, model.getTexCoords(),
                GLES20.GL_STATIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        final int mCubePositionsBufferIdx = buffers[0];
        final int mCubeNormalsBufferIdx = buffers[1];
        final int mCubeTexCoordsBufferIdx = buffers[2];*/

        // Pass in the position information
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, model.mCubePositionsBufferIdx);
        GLES20.glEnableVertexAttribArray(positionAttribute);
        GLES20.glVertexAttribPointer(positionAttribute, 3, GLES20.GL_FLOAT, false, 0, 0);

        // Pass in the normal information
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, model.mCubeNormalsBufferIdx);
        GLES20.glEnableVertexAttribArray(normalAttribute);
        GLES20.glVertexAttribPointer(normalAttribute, 3, GLES20.GL_FLOAT, false, 0, 0);

        // Pass in the texture information
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, model.mCubeTexCoordsBufferIdx);
        GLES20.glEnableVertexAttribArray(mTextureCoordinateHandle);
        GLES20.glVertexAttribPointer(mTextureCoordinateHandle, 2, GLES20.GL_FLOAT, false,
                0, 0);

        // Clear the currently bound buffer (so future OpenGL calls do not use this buffer).
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        // Draw the cubes.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, model.getVertices().capacity());

    }
}