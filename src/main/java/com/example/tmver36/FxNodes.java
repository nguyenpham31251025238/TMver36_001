package com.example.tmver36;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Safe scene-graph traversal helpers that work both during FXML initialize()
 * (before Skin attachment or Scene attachment) and after Skin attachment.
 */
public final class FxNodes {

    private FxNodes() {
    }

    public static Parent rootOf(Node... candidates) {
        if (candidates == null) {
            return null;
        }
        for (Node candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            Parent current = candidate instanceof Parent p ? p : candidate.getParent();
            while (current != null && current.getParent() != null) {
                current = current.getParent();
            }
            if (current != null) {
                return current;
            }
            if (candidate.getScene() != null && candidate.getScene().getRoot() != null) {
                return candidate.getScene().getRoot();
            }
        }
        return null;
    }

    public static List<Node> allNodes(Node root) {
        if (root == null) {
            return List.of();
        }
        List<Node> out = new ArrayList<>();
        Set<Node> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        collectNodes(root, out, visited);
        return out;
    }

    private static void collectNodes(Node node, List<Node> out, Set<Node> visited) {
        if (node == null || !visited.add(node)) {
            return;
        }
        out.add(node);

        if (node instanceof ScrollPane scrollPane && scrollPane.getContent() != null) {
            collectNodes(scrollPane.getContent(), out, visited);
        }
        if (node instanceof TabPane tabPane) {
            for (Tab tab : tabPane.getTabs()) {
                if (tab != null) {
                    collectNodes(tab.getGraphic(), out, visited);
                    collectNodes(tab.getContent(), out, visited);
                }
            }
        }
        if (node instanceof TitledPane titledPane) {
            collectNodes(titledPane.getGraphic(), out, visited);
            collectNodes(titledPane.getContent(), out, visited);
        }
        if (node instanceof SplitPane splitPane) {
            for (Node item : splitPane.getItems()) {
                collectNodes(item, out, visited);
            }
        }
        if (node instanceof Labeled labeled && labeled.getGraphic() != null) {
            collectNodes(labeled.getGraphic(), out, visited);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectNodes(child, out, visited);
            }
        }
    }

    public static <T extends Node> List<T> findAll(Node root, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Node node : allNodes(root)) {
            if (type.isInstance(node)) {
                result.add(type.cast(node));
            }
        }
        return result;
    }

    public static List<Node> findAllByStyleClass(Node root, String styleClass) {
        List<Node> result = new ArrayList<>();
        for (Node node : allNodes(root)) {
            if (node.getStyleClass().contains(styleClass)) {
                result.add(node);
            }
        }
        return result;
    }

    public static <T extends Node> List<T> findAllByStyleClass(Node root, String styleClass, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Node node : allNodes(root)) {
            if (type.isInstance(node) && node.getStyleClass().contains(styleClass)) {
                result.add(type.cast(node));
            }
        }
        return result;
    }

    public static <T extends Node> T findFirstByStyleClass(Node root, String styleClass, Class<T> type) {
        for (Node node : allNodes(root)) {
            if (type.isInstance(node) && node.getStyleClass().contains(styleClass)) {
                return type.cast(node);
            }
        }
        return null;
    }
}
