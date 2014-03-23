package com.dopelives.dopestreamer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Initialiser extends Application {

    /** The absolute path to the folder containing the GUI resources */
    public static final String RESOURCE_FOLDER = "/com/dopelives/dopestreamer/res/";
    /** The absolute path to the folder containing the GUI image resources */
    public static final String IMAGE_FOLDER = RESOURCE_FOLDER + "images/";

    /** The window title */
    private static final String TITLE = "Dopestreamer 420";

    public static void main(final String[] args) {
        Application.launch(Initialiser.class, args);
    }

    @Override
    public void start(final Stage stage) throws Exception {
        final Parent root = FXMLLoader.load(getClass().getResource(RESOURCE_FOLDER + "main_window.fxml"));

        stage.setTitle(TITLE);
        stage.setScene(new Scene(root, 300, 275));
        stage.show();
    }

}
