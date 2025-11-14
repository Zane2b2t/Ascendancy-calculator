package org.example.physics;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.IntStream;

public class RCSSimulator3D extends Application {

    // Reference objects with known RCS values (in m²)
    static class ReferenceObject {
        String name;
        MeshData mesh;
        double referenceRcs; // in m² at reference frequency
        double referenceFreqGHz;

        ReferenceObject(String name, MeshData mesh, double rcs, double freqGHz) {
            this.name = name;
            this.mesh = mesh;
            this.referenceRcs = rcs;
            this.referenceFreqGHz = freqGHz;
        }
    }

    static class Vertex { float x, y, z; Vertex(float x,float y,float z){this.x=x;this.y=y;this.z=z;} }
    static class Face { int v1,v2,v3; Face(int a,int b,int c){v1=a; v2=b; v3=c;} }
    static class MeshData { List<Vertex> vertices = new ArrayList<>(); List<Face> faces = new ArrayList<>(); }
    static class EdgeKey {
        int v1, v2;
        EdgeKey(int a, int b) { v1 = Math.min(a,b); v2 = Math.max(a,b); }
        public boolean equals(Object o) {
            if (!(o instanceof EdgeKey)) return false;
            EdgeKey e = (EdgeKey)o;
            return v1==e.v1 && v2==e.v2;
        }
        public int hashCode() { return v1*100000+v2; }
    }
    static class EdgeInfo {
        double[] v1, v2;
        List<Integer> adjacentFaces = new ArrayList<>();
        double length;
        EdgeInfo(double[] a, double[] b) { v1=a; v2=b; length=dist(a,b); }
        static double dist(double[] a, double[] b) {
            return Math.sqrt(Math.pow(a[0]-b[0],2)+Math.pow(a[1]-b[1],2)+Math.pow(a[2]-b[2],2));
        }
    }

    private MeshData meshData;
    private MeshView meshView;
    private double[] rcsLinear;
    private double[] rcsDb;
    private Canvas polarCanvas;
    private GraphicsContext gc;
    private LineChart<Number,Number> lineChart;
    private XYChart.Series<Number,Number> series;
    private PerspectiveCamera camera;
    private Slider angleSlider, yawSlider, pitchSlider, rollSlider;
    private TextField freqField;
    private Label currentRcsLabel;
    private Label modelInfoLabel;
    private final DecimalFormat df = new DecimalFormat("#0.00");
    private static final double C = 299792458.0;

    private double cameraDistance = -800;
    private static final double MIN_CAMERA_DISTANCE = -50;
    private static final double MAX_CAMERA_DISTANCE = -5000;

    private boolean isComputing = false;
    private List<ReferenceObject> referenceObjects = new ArrayList<>();
    private ReferenceObject currentReference = null;
    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        initializeReferenceObjects();

        BorderPane root = new BorderPane();

        // CENTER 3D mesh
        Group sceneRoot = new Group();
        SubScene subScene = new SubScene(sceneRoot, 800, 600, true, SceneAntialiasing.BALANCED);
        camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(10000);
        camera.setTranslateZ(cameraDistance);
        sceneRoot.getChildren().add(camera);
        subScene.setCamera(camera);
        subScene.setFill(Color.rgb(20,20,20));

        subScene.setOnScroll((ScrollEvent event) -> {
            double delta = event.getDeltaY();
            double zoomFactor = 1.08;

            if (delta < 0) {
                cameraDistance *= zoomFactor;
            } else {
                cameraDistance /= zoomFactor;
            }

            cameraDistance = Math.max(MAX_CAMERA_DISTANCE, Math.min(MIN_CAMERA_DISTANCE, cameraDistance));
            camera.setTranslateZ(cameraDistance);
        });

        StackPane centerStack = new StackPane(subScene);
        subScene.widthProperty().bind(centerStack.widthProperty());
        subScene.heightProperty().bind(centerStack.heightProperty());
        root.setCenter(centerStack);

        // RIGHT: polar plot + rotation sliders
        polarCanvas = new Canvas(420, 420);
        gc = polarCanvas.getGraphicsContext2D();

        yawSlider = createSlider("Yaw");
        pitchSlider = createSlider("Pitch");
        rollSlider = createSlider("Roll");

        Slider zoomSlider = new Slider(MAX_CAMERA_DISTANCE, MIN_CAMERA_DISTANCE, cameraDistance);
        zoomSlider.setPrefWidth(400);
        zoomSlider.setShowTickLabels(false);
        zoomSlider.setShowTickMarks(true);
        zoomSlider.valueProperty().addListener((obs, ov, nv) -> {
            cameraDistance = nv.doubleValue();
            camera.setTranslateZ(cameraDistance);
        });

        VBox rotationBox = new VBox(8,
                new HBox(8,new Label("Yaw: "), yawSlider),
                new HBox(8,new Label("Pitch: "), pitchSlider),
                new HBox(8,new Label("Roll: "), rollSlider),
                new Separator(),
                new HBox(8,new Label("Zoom: "), zoomSlider)
        );
        rotationBox.setPadding(new Insets(10));

        BorderPane polarPane = new BorderPane();
        polarPane.setCenter(polarCanvas);
        polarPane.setBottom(rotationBox);
        root.setRight(polarPane);

        // BOTTOM: line chart + radar slider
        NumberAxis xAxis = new NumberAxis(0,360,30);
        xAxis.setLabel("Angle (°)");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("RCS (dBsm)");
        lineChart = new LineChart<>(xAxis,yAxis);
        lineChart.setPrefHeight(250);
        lineChart.setCreateSymbols(false);
        lineChart.setAnimated(false);
        series = new XYChart.Series<>();
        lineChart.getData().add(series);

        angleSlider = new Slider(0,359,0);
        angleSlider.setPrefWidth(700);
        angleSlider.setShowTickLabels(true);
        angleSlider.setShowTickMarks(true);
        angleSlider.setMajorTickUnit(45);
        angleSlider.setMinorTickCount(4);

        currentRcsLabel = new Label("Angle: 0°  RCS: -- dBsm");
        HBox bottomBox = new HBox(12, angleSlider, currentRcsLabel);
        bottomBox.setPadding(new Insets(8));
        BorderPane bottomPane = new BorderPane();
        bottomPane.setTop(bottomBox);
        bottomPane.setCenter(lineChart);
        root.setBottom(bottomPane);

        // TOP: frequency + recompute + object selection
        freqField = new TextField("10.0");
        freqField.setPrefWidth(80);
        Label freqLbl = new Label("Freq (GHz):");

        ComboBox<ReferenceObject> objectCombo = new ComboBox<>();
        objectCombo.setPrefWidth(200);
        for(ReferenceObject ref : referenceObjects) {
            objectCombo.getItems().add(ref);
        }
        objectCombo.setConverter(new javafx.util.StringConverter<ReferenceObject>() {
            @Override
            public String toString(ReferenceObject obj) {
                return obj == null ? "None" : obj.name + " (" + df.format(obj.referenceRcs) + " m²)";
            }
            @Override
            public ReferenceObject fromString(String s) { return null; }
        });

        Button loadObjectBtn = new Button("Load Reference");
        loadObjectBtn.setOnAction(e -> {
            ReferenceObject selected = objectCombo.getSelectionModel().getSelectedItem();
            if(selected != null) {
                currentReference = selected;
                loadReferenceObject(selected, sceneRoot);
            }
        });

        Button customObjBtn = new Button("Load Custom OBJ");
        customObjBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Open OBJ");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("OBJ","*.obj"));
            File file = chooser.showOpenDialog(primaryStage);
            if(file != null) {
                loadCustomObject(file.getAbsolutePath(), sceneRoot);
            }
        });

        Button recomputeBtn = new Button("Recompute RCS");
        Label zoomHint = new Label("(Use mouse wheel to zoom)");
        zoomHint.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");

        modelInfoLabel = new Label("No model loaded");
        modelInfoLabel.setStyle("-fx-text-fill: #AAAAAA; -fx-font-size: 9px;");

        HBox topBox = new HBox(8, freqLbl, freqField, objectCombo, loadObjectBtn, customObjBtn, recomputeBtn, zoomHint);
        topBox.setPadding(new Insets(6));

        VBox topVBox = new VBox(4, topBox, modelInfoLabel);
        topVBox.setPadding(new Insets(6));
        root.setTop(topVBox);

        Scene scene = new Scene(root, 1300, 800, true);
        primaryStage.setScene(scene);
        primaryStage.setTitle("RCS Simulator (PO+PTD) Radar View");
        primaryStage.show();

        recomputeBtn.setOnAction(a -> new Thread(this::computeAndDisplayRcs).start());
    }

    private void initializeReferenceObjects() {
        // Perfect sphere - RCS = πr² at all frequencies
        // Sphere with radius 1m: RCS = π ≈ 3.14159 m²
        referenceObjects.add(new ReferenceObject(
                "Sphere (r=1m)",
                createSphere(1.0f, 20),
                Math.PI,
                10.0
        ));

        // Flat square plate - RCS = (4π*A²)/λ²
        // 1m x 1m square at 10 GHz: RCS ≈ 13.3 m²
        referenceObjects.add(new ReferenceObject(
                "Square Plate (1m)",
                createSquarePlate(1.0f),
                13.3,
                10.0
        ));

        // Flat square plate - 2m x 2m
        referenceObjects.add(new ReferenceObject(
                "Square Plate (2m)",
                createSquarePlate(2.0f),
                53.2,
                10.0
        ));

        // Corner reflector (trihedral)
        // Theoretical RCS at 10 GHz for 1m edges: ~40 m²
        referenceObjects.add(new ReferenceObject(
                "Corner Reflector (1m)",
                createCornerReflector(1.0f),
                40.0,
                10.0
        ));

        // Cylinder
        referenceObjects.add(new ReferenceObject(
                "Cylinder (r=0.5m, h=2m)",
                createCylinder(0.5f, 2.0f, 16),
                3.5,
                10.0
        ));
    }

    private MeshData createSphere(float radius, int segments) {
        MeshData mesh = new MeshData();
        for(int i = 0; i <= segments; i++) {
            float phi = (float)(Math.PI * i / segments);
            for(int j = 0; j <= segments; j++) {
                float theta = (float)(2 * Math.PI * j / segments);
                float x = radius * (float)(Math.sin(phi) * Math.cos(theta));
                float y = radius * (float)(Math.sin(phi) * Math.sin(theta));
                float z = radius * (float)Math.cos(phi);
                mesh.vertices.add(new Vertex(x, y, z));
            }
        }

        for(int i = 0; i < segments; i++) {
            for(int j = 0; j < segments; j++) {
                int a = i * (segments + 1) + j;
                int b = a + segments + 1;
                int c = a + 1;
                int d = b + 1;
                mesh.faces.add(new Face(a, b, c));
                mesh.faces.add(new Face(c, b, d));
            }
        }
        return mesh;
    }

    private MeshData createSquarePlate(float size) {
        MeshData mesh = new MeshData();
        float half = size / 2;
        mesh.vertices.add(new Vertex(-half, 0, -half));
        mesh.vertices.add(new Vertex(half, 0, -half));
        mesh.vertices.add(new Vertex(half, 0, half));
        mesh.vertices.add(new Vertex(-half, 0, half));
        mesh.faces.add(new Face(0, 1, 2));
        mesh.faces.add(new Face(0, 2, 3));
        return mesh;
    }

    private MeshData createCornerReflector(float size) {
        MeshData mesh = new MeshData();
        mesh.vertices.add(new Vertex(0, 0, 0));
        mesh.vertices.add(new Vertex(size, 0, 0));
        mesh.vertices.add(new Vertex(0, size, 0));
        mesh.vertices.add(new Vertex(0, 0, size));
        mesh.faces.add(new Face(0, 1, 2));
        mesh.faces.add(new Face(0, 1, 3));
        mesh.faces.add(new Face(0, 2, 3));
        mesh.faces.add(new Face(1, 2, 3));
        return mesh;
    }

    private MeshData createCylinder(float radius, float height, int segments) {
        MeshData mesh = new MeshData();
        float halfH = height / 2;
        for(int i = 0; i < segments; i++) {
            float angle = (float)(2 * Math.PI * i / segments);
            float x = radius * (float)Math.cos(angle);
            float z = radius * (float)Math.sin(angle);
            mesh.vertices.add(new Vertex(x, halfH, z));
            mesh.vertices.add(new Vertex(x, -halfH, z));
        }
        for(int i = 0; i < segments; i++) {
            int a = (i * 2);
            int b = ((i + 1) % segments) * 2;
            int c = a + 1;
            int d = b + 1;
            mesh.faces.add(new Face(a, b, d));
            mesh.faces.add(new Face(a, d, c));
        }
        return mesh;
    }

    private void loadReferenceObject(ReferenceObject ref, Group sceneRoot) {
        meshData = ref.mesh;
        if(meshView != null) sceneRoot.getChildren().remove(meshView);
        meshView = createMeshView(meshData);
        sceneRoot.getChildren().add(meshView);

        setupEventListeners();
        modelInfoLabel.setText("Loaded: " + ref.name + " | Reference RCS: " + df.format(ref.referenceRcs) + " m² @ " + ref.referenceFreqGHz + " GHz");
        new Thread(this::computeAndDisplayRcs).start();
    }

    private void loadCustomObject(String path, Group sceneRoot) {
        new Thread(() -> {
            meshData = loadOBJ(path);
            if(meshData.vertices.isEmpty() || meshData.faces.isEmpty()) {
                Platform.runLater(() -> showError("Failed to load OBJ file"));
                return;
            }

            currentReference = null;
            Platform.runLater(() -> {
                if(meshView != null) sceneRoot.getChildren().remove(meshView);
                meshView = createMeshView(meshData);
                sceneRoot.getChildren().add(meshView);
                setupEventListeners();
                modelInfoLabel.setText("Loaded: Custom OBJ | Vertices: " + meshData.vertices.size() + " | Faces: " + meshData.faces.size());
                new Thread(this::computeAndDisplayRcs).start();
            });
        }).start();
    }

    private void setupEventListeners() {
        ChangeListener<Number> visualUpdateListener = (obs, ov, nv) -> {
            if(!isComputing) {
                updateMeshRotation(yawSlider.getValue(), pitchSlider.getValue(), rollSlider.getValue());
                int angle = (int)angleSlider.getValue();
                updateViewForAngle(angle);
            }
        };

        yawSlider.valueProperty().addListener(visualUpdateListener);
        pitchSlider.valueProperty().addListener(visualUpdateListener);
        rollSlider.valueProperty().addListener(visualUpdateListener);
        angleSlider.valueProperty().addListener(visualUpdateListener);

        yawSlider.setOnMouseReleased(e -> new Thread(this::computeAndDisplayRcs).start());
        pitchSlider.setOnMouseReleased(e -> new Thread(this::computeAndDisplayRcs).start());
        rollSlider.setOnMouseReleased(e -> new Thread(this::computeAndDisplayRcs).start());
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private Slider createSlider(String label){
        Slider s = new Slider(0,360,0);
        s.setPrefWidth(400);
        s.setShowTickLabels(true);
        s.setShowTickMarks(true);
        s.setMajorTickUnit(90);
        s.setMinorTickCount(4);
        return s;
    }

    private void computeAndDisplayRcs() {
        if(meshData==null || isComputing) return;
        isComputing = true;

        double freqGHz;
        try { freqGHz=Double.parseDouble(freqField.getText().trim()); if(freqGHz<=0) freqGHz=10.0; }
        catch(Exception ex){ freqGHz=10.0; Platform.runLater(()->freqField.setText("10.0")); }
        final double freq=freqGHz*1e9;
        final double lambda=C/freq;
        final double k = 2.0 * Math.PI / lambda;

        int nAngles=360;
        double[] localRcs = new double[nAngles];

        // Compute rotated vertices
        double yaw = Math.toRadians(yawSlider.getValue());
        double pitch = Math.toRadians(pitchSlider.getValue());
        double roll = Math.toRadians(rollSlider.getValue());
        double cosY=Math.cos(yaw), sinY=Math.sin(yaw);
        double cosP=Math.cos(pitch), sinP=Math.sin(pitch);
        double cosR=Math.cos(roll), sinR=Math.sin(roll);

        List<double[]> rotatedVerts = new ArrayList<>();
        for(Vertex v: meshData.vertices){
            double x=v.x, y=v.y, z=v.z;
            double xr = cosY*x + sinY*z;
            double yr = sinP*sinY*x + cosP*y - sinP*cosY*z;
            double zr = -cosP*sinY*x + sinP*y + cosP*cosY*z;
            double xr2 = cosR*xr - sinR*yr;
            double yr2 = sinR*xr + cosR*yr;
            double zr2 = zr;
            rotatedVerts.add(new double[]{xr2, yr2, zr2});
        }

        int F=meshData.faces.size();

        // Face data: normal and area
        List<double[]> faceNormals = new ArrayList<>();
        List<Double> faceAreas = new ArrayList<>();
        List<double[]> faceCenters = new ArrayList<>();

        for(int i=0;i<F;i++){
            Face face = meshData.faces.get(i);
            double[] v1=rotatedVerts.get(face.v1);
            double[] v2=rotatedVerts.get(face.v2);
            double[] v3=rotatedVerts.get(face.v3);

            double ux=v2[0]-v1[0], uy=v2[1]-v1[1], uz=v2[2]-v1[2];
            double vx=v3[0]-v1[0], vy=v3[1]-v1[1], vz=v3[2]-v1[2];
            double cx=uy*vz-uz*vy, cy=uz*vx-ux*vz, cz=ux*vy-uy*vx;
            double triArea=0.5*Math.sqrt(cx*cx+cy*cy+cz*cz);
            double norm=Math.sqrt(cx*cx+cy*cy+cz*cz);

            double[] normal = new double[3];
            if(norm>0) {
                normal[0]=cx/norm;
                normal[1]=cy/norm;
                normal[2]=cz/norm;
            }
            faceNormals.add(normal);
            faceAreas.add(triArea);
            faceCenters.add(new double[]{(v1[0]+v2[0]+v3[0])/3.0, (v1[1]+v2[1]+v3[1])/3.0, (v1[2]+v2[2]+v3[2])/3.0});
        }

        // Build edge map with rotated vertices
        Map<EdgeKey, EdgeInfo> edgeMap = new HashMap<>();
        for(int fi=0; fi<F; fi++) {
            Face f = meshData.faces.get(fi);
            int[] verts = {f.v1, f.v2, f.v3};

            for(int i=0; i<3; i++) {
                int v1idx = verts[i];
                int v2idx = verts[(i+1)%3];
                EdgeKey key = new EdgeKey(v1idx, v2idx);

                EdgeInfo info = edgeMap.get(key);
                if(info == null) {
                    info = new EdgeInfo(rotatedVerts.get(v1idx), rotatedVerts.get(v2idx));
                    edgeMap.put(key, info);
                }
                info.adjacentFaces.add(fi);
            }
        }

        IntStream.range(0,nAngles).parallel().forEach(thetaDeg->{
            double theta=Math.toRadians(thetaDeg);
            double ix=Math.cos(theta), iy=Math.sin(theta), iz=0.0;

            // Complex field accumulation (real and imaginary parts)
            double realSum = 0.0;
            double imagSum = 0.0;

            // Physical Optics - coherent summation
            for(int fi=0; fi<F; fi++){
                double[] normal = faceNormals.get(fi);
                double ndoti = normal[0]*ix + normal[1]*iy + normal[2]*iz;
                if(ndoti <= 0) continue;

                double A = faceAreas.get(fi);
                double[] center = faceCenters.get(fi);

                // Phase from path length difference
                double phase = k * (center[0]*ix + center[1]*iy + center[2]*iz);

                // PO amplitude
                double amplitude = Math.sqrt(4*Math.PI*A*A*ndoti*ndoti/(lambda*lambda));

                realSum += amplitude * Math.cos(phase);
                imagSum += amplitude * Math.sin(phase);
            }

            // Edge diffraction - creates the spiky pattern
            for(EdgeInfo edge : edgeMap.values()) {
                double[] v1 = edge.v1;
                double[] v2 = edge.v2;

                // Edge vector
                double ex = v2[0] - v1[0];
                double ey = v2[1] - v1[1];
                double ez = v2[2] - v1[2];
                double edgeLen = edge.length;

                if(edgeLen < 1e-10) continue;

                ex /= edgeLen; ey /= edgeLen; ez /= edgeLen;

                // Edge center
                double ecx = (v1[0] + v2[0]) / 2.0;
                double ecy = (v1[1] + v2[1]) / 2.0;
                double ecz = (v1[2] + v2[2]) / 2.0;

                // Incident angle to edge
                double edgeDotInc = Math.abs(ex*ix + ey*iy + ez*iz);
                double sinAlpha = Math.sqrt(Math.max(0, 1.0 - edgeDotInc*edgeDotInc));

                if(sinAlpha < 0.05) continue; // Skip near-parallel

                // Edge phase
                double edgePhase = k * (ecx*ix + ecy*iy + ecz*iz);

                // Determine if edge is boundary or interior
                boolean isBoundary = edge.adjacentFaces.size() == 1;
                double wedgeAngle = isBoundary ? Math.PI : Math.PI * 0.5; // Simplified

                // PTD diffraction coefficient (Keller-like)
                double diffCoeff = Math.sin(wedgeAngle/2.0) / Math.sqrt(2.0 * Math.PI * k * edgeLen * sinAlpha + 0.01);

                // Edge contribution amplitude
                double edgeAmplitude = diffCoeff * edgeLen * Math.sqrt(k) * sinAlpha;

                // Multiple scattering effects - creates interference
                double multiScatter = 1.0;
                for(int harmonic = 1; harmonic <= 3; harmonic++) {
                    double harmonicPhase = harmonic * k * edgeLen * sinAlpha;
                    multiScatter += 0.3 / harmonic * Math.cos(harmonicPhase);
                }

                edgeAmplitude *= Math.abs(multiScatter);

                realSum += edgeAmplitude * Math.cos(edgePhase);
                imagSum += edgeAmplitude * Math.sin(edgePhase);
            }

            // RCS from coherent field magnitude squared
            double fieldMagnitude = Math.sqrt(realSum*realSum + imagSum*imagSum);
            localRcs[thetaDeg] = fieldMagnitude * fieldMagnitude;
        });

        double[] localDb=new double[localRcs.length];
        for(int i=0;i<localRcs.length;i++) {
            localDb[i] = 10*Math.log10(Math.max(localRcs[i], 1e-20));
        }

        rcsLinear=localRcs; rcsDb=localDb;
        isComputing = false;

        Platform.runLater(()->{
            series.getData().clear();
            for(int i=0;i<rcsDb.length;i++) series.getData().add(new XYChart.Data<>(i,rcsDb[i]));
            updateViewForAngle((int)angleSlider.getValue());
            drawPolarGraph((int)angleSlider.getValue());
        });
    }

    private void updateMeshRotation(double yaw, double pitch, double roll){
        if(meshView==null) return;
        meshView.getTransforms().clear();
        meshView.getTransforms().addAll(
                new Rotate(-90, Rotate.X_AXIS),
                new Rotate(yaw, Rotate.Y_AXIS),
                new Rotate(pitch, Rotate.X_AXIS),
                new Rotate(roll, Rotate.Z_AXIS)
        );
    }

    private void updateViewForAngle(int angle){
        if(rcsLinear==null||rcsDb==null||meshView==null) return;

        meshView.setRotationAxis(Rotate.Y_AXIS);
        meshView.setRotate(angle);

        double maxDb=Double.NEGATIVE_INFINITY,minDb=Double.POSITIVE_INFINITY;
        for(double d:rcsDb){ if(d>maxDb)maxDb=d; if(d<minDb)minDb=d; }
        double valueDb=rcsDb[angle];
        double colorFactor=(valueDb-minDb)/(Math.max(1e-6,maxDb-minDb));
        colorFactor=Math.max(0.0,Math.min(1.0,colorFactor));
        PhongMaterial mat=new PhongMaterial();
        Color col=Color.hsb(240-colorFactor*240,1.0,0.9);
        mat.setDiffuseColor(col);
        meshView.setMaterial(mat);

        currentRcsLabel.setText("Angle: "+angle+"°    RCS: "+df.format(valueDb)+" dBsm");

        drawPolarGraph(angle);
    }

    private void drawPolarGraph(int currentAngle){
        if(rcsDb==null) return;
        double w=polarCanvas.getWidth(), h=polarCanvas.getHeight();
        double cx=w/2.0, cy=h/2.0;
        double radius=Math.min(cx,cy)*0.9;
        gc.setFill(Color.rgb(25,25,25));
        gc.fillRect(0,0,w,h);

        double maxDb=Double.NEGATIVE_INFINITY,minDb=Double.POSITIVE_INFINITY;
        for(double d:rcsDb){ if(d>maxDb) maxDb=d; if(d<minDb) minDb=d; }
        if(maxDb-minDb<30) maxDb=minDb+30;

        gc.setStroke(Color.grayRgb(90)); gc.setLineWidth(1);
        int rings=5;
        for(int r=1;r<=rings;r++){
            double rr=radius*(r/(double)rings);
            gc.strokeOval(cx-rr,cy-rr,rr*2,rr*2);
            double tickDb=minDb+(maxDb-minDb)*(r/(double)rings);
            gc.setFill(Color.grayRgb(180));
            gc.fillText(df.format(tickDb)+" dB", cx+6, cy-rr+10);
        }

        gc.setStroke(Color.grayRgb(70));
        for(int a=0;a<360;a+=30){
            double ang=Math.toRadians(a);
            double x=cx+radius*Math.cos(ang);
            double y=cy-radius*Math.sin(ang);
            gc.strokeLine(cx,cy,x,y);
            double lx=cx+(radius+12)*Math.cos(ang);
            double ly=cy-(radius+12)*Math.sin(ang);
            gc.setFill(Color.grayRgb(180));
            gc.fillText(a+"°", lx-10, ly+4);
        }

        gc.setStroke(Color.ORANGERED); gc.setLineWidth(2); gc.beginPath();
        for(int i=0;i<rcsDb.length;i++){
            double norm=(rcsDb[i]-minDb)/(maxDb-minDb);
            double r=norm*radius;
            double th=Math.toRadians(i);
            double x=cx+r*Math.cos(th);
            double y=cy-r*Math.sin(th);
            if(i==0) gc.moveTo(x,y); else gc.lineTo(x,y);
        }
        gc.closePath(); gc.stroke();

        double normCur=(rcsDb[currentAngle]-minDb)/(maxDb-minDb);
        double rCur=normCur*radius;
        double ax=cx+rCur*Math.cos(Math.toRadians(currentAngle));
        double ay=cy-rCur*Math.sin(Math.toRadians(currentAngle));
        gc.setFill(Color.CYAN);
        gc.fillOval(ax-5,ay-5,10,10);

        gc.setFill(Color.WHITE);
        gc.fillText("Angle: "+currentAngle+"°",8,16);
        gc.fillText("RCS: "+df.format(rcsDb[currentAngle])+" dBsm",8,32);
    }

    private MeshView createMeshView(MeshData mesh){
        TriangleMesh t=new TriangleMesh();
        for(Vertex v:mesh.vertices) t.getPoints().addAll(v.x,v.y,v.z);
        t.getTexCoords().addAll(0,0);
        for(Face f:mesh.faces) t.getFaces().addAll(f.v1,0,f.v2,0,f.v3,0);
        MeshView mv=new MeshView(t);
        PhongMaterial mat=new PhongMaterial(Color.LIGHTGRAY);
        mv.setMaterial(mat);
        mv.getTransforms().addAll(new Rotate(-90, Rotate.X_AXIS));
        return mv;
    }

    private MeshData loadOBJ(String path){
        MeshData mesh=new MeshData();
        try(BufferedReader br=new BufferedReader(new FileReader(path))){
            String line;
            List<Vertex> verts=new ArrayList<>();
            while((line=br.readLine())!=null){
                line=line.trim();
                if(line.startsWith("v ")){
                    String[] p=line.split("\\s+");
                    float x=Float.parseFloat(p[1]);
                    float y=Float.parseFloat(p[2]);
                    float z=Float.parseFloat(p[3]);
                    verts.add(new Vertex(x,y,z));
                } else if(line.startsWith("f ")){
                    String[] p=line.split("\\s+");
                    int a=Integer.parseInt(p[1].split("/")[0])-1;
                    int b=Integer.parseInt(p[2].split("/")[0])-1;
                    int c=Integer.parseInt(p[3].split("/")[0])-1;
                    mesh.faces.add(new Face(a,b,c));
                }
            }
            mesh.vertices=verts;
        } catch(IOException ex){ System.out.println("OBJ load error: "+ex.getMessage()); }
        return mesh;
    }

    public static void main(String[] args){ launch(args); }
}