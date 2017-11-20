package lutec.tec.hologram.obj;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.vecmath.Vector2f;
import javax.vecmath.Vector3f;

/**
 * Created by josea on 9/26/2017.
 */

public class OBJ_Loader {

    private static List<Vector3f> in_vertices = new ArrayList<Vector3f>();
    private static List<Vector2f> in_textures = new ArrayList<Vector2f>();
    private static List<Vector3f> in_normals = new ArrayList<Vector3f>();

    private static List<Float> out_vertices = new ArrayList<Float>();
    private static List<Float> out_textures = new ArrayList<Float>();
    private static List<Float> out_normals = new ArrayList<Float>();

    private static List<Short> indices = new ArrayList<Short>();

    public static Model loadModel(InputStream is){
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        Model m = new Model();
        String line;

        float[] verticesArray = null;
        float[] normalsArray = null;
        float[] textureArray = null;
        short[] indicesArray = null;

        try{
            while (true) {
                line = reader.readLine();
                //Log.d("Line", line);
                if(line == null){
                    break;
                }
                String[] currentLine = line.split("[ ]+");
                if (line.startsWith("v ")) {
                    Vector3f vertex = new Vector3f(Float.parseFloat(currentLine[1]),
                            Float.parseFloat(currentLine[2]), Float.parseFloat(currentLine[3]));
                    in_vertices.add(vertex);
                } else if (line.startsWith("vt ")) {
                    Vector2f texture = new Vector2f(Float.parseFloat(currentLine[1]),
                            Float.parseFloat(currentLine[2]));
                    in_textures.add(texture);
                } else if (line.startsWith("vn ")) {
                    Vector3f normal = new Vector3f(Float.parseFloat(currentLine[1]),
                            Float.parseFloat(currentLine[2]), Float.parseFloat(currentLine[3]));
                    in_normals.add(normal);
                } else if (line.startsWith("f ")) {
                    String[] faceLine = line.split("[ ]+");

                    String[] vertex1 = faceLine[1].split("/");
                    String[] vertex2 = faceLine[2].split("/");
                    String[] vertex3 = faceLine[3].split("/");

                    processVertex(vertex1);
                    processVertex(vertex2);
                    processVertex(vertex3);
                    //line = reader.readLine();
                }
            }
            reader.close();


        }catch (Exception e){
            e.printStackTrace();
        }

        verticesArray = new float[out_vertices.size()];
        normalsArray = new float[out_normals.size()];
        textureArray = new float[out_textures.size()];
        indicesArray = new short[indices.size()];

        int vertexPointer = 0;
        for(float vertex_val: out_vertices){
            verticesArray[vertexPointer++] = vertex_val;
        }

        int normalPointer = 0;
        for(float normal_val: out_normals){
            normalsArray[normalPointer++] = normal_val;
        }

        int texturePointer = 0;
        for(float texture_val: out_textures){
            textureArray[texturePointer++] = texture_val;
        }

        for(int i=0;i<indices.size();i++){
            indicesArray[i] = indices.get(i);
        }

        m.vertices = verticesArray;
        m.texCoords = textureArray;
        m.normals = normalsArray;
        m.indices = indicesArray;
        return m;

    }
    private  static void processVertex(String[] vertexData){

        Vector3f currentVert = in_vertices.get(Integer.parseInt(vertexData[0])-1);
        out_vertices.add(currentVert.x);
        out_vertices.add(currentVert.y);
        out_vertices.add(currentVert.z);

        Vector3f currentNorm = in_normals.get(Integer.parseInt(vertexData[2])-1);
        out_normals.add(currentNorm.x);
        out_normals.add(currentNorm.y);
        out_normals.add(currentNorm.z);

        Vector2f currentTex = in_textures.get(Integer.parseInt(vertexData[1])-1);
        out_textures.add(currentTex.x);
        out_textures.add(currentTex.y);

    }
}