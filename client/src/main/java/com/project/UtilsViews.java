package com.project;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import java.util.HashMap;
import java.util.Map;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

public class UtilsViews {

    public static final StackPane parentContainer = new StackPane();
    // Controlador de cada vista, guardado por su nombre
    private static final Map<String, Object> controllers = new HashMap<>();
    
    private static String currentView = "";

    public static String getCurrentView() {
        return currentView;
    }

    public static void setView(String name) {
        currentView = name;
        for (Node view : parentContainer.getChildren()) {
            boolean show = name.equals(view.getId());
            view.setVisible(show);
            view.setManaged(show);
        }
    }

    // Carga un fxml, le da un nombre y lo añade al contenedor (escondido)
    public static void addView(Class<?> cls, String name, String path) throws Exception {
        FXMLLoader loader = new FXMLLoader(cls.getResource(path));
        Parent view = loader.load();
        view.setId(name);
        view.setVisible(false);
        view.setManaged(false);
        parentContainer.getChildren().add(view);
        controllers.put(name, loader.getController());
    }

    public static Object getController(String name) {
        return controllers.get(name);
    }
}
