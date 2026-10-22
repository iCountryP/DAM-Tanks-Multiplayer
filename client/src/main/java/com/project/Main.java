package com.project;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        
        UtilsViews.addView(getClass(), "ViewConnect", "/assets/viewConnect.fxml");
        UtilsViews.addView(getClass(), "ViewWaiting", "/assets/viewWaiting.fxml");
        UtilsViews.addView(getClass(), "ViewResult", "/assets/viewResult.fxml");
        
        UtilsViews.setView("ViewConnect");
        
        Scene scene = new Scene(UtilsViews.parentContainer);
        
        stage.setScene(scene);
        stage.setTitle("Tanks");
        stage.setResizable(false);

        if (!System.getProperty("os.name").contains("Mac")) {
            Image icon = new Image(getClass().getResourceAsStream("/icons/icon.png"));
            stage.getIcons().add(icon);
        }

        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
