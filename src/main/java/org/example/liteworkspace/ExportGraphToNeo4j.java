package org.example.liteworkspace;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import org.neo4j.graphdb.*;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ExportGraphToNeo4j {

    private static DatabaseManagementService managementService;

    private static enum RelTypes implements RelationshipType {
        CONTAINS, CALLS, EXTENDS, IMPLEMENTS
    }

    public static void main(String[] args) throws Exception {
        String src = "src/main/java";
        String graphDbPath = "graphdb";

        // 启动嵌入式 Neo4j
        managementService = new DatabaseManagementServiceBuilder(new File(graphDbPath).toPath()).build();
        GraphDatabaseService graphDb = managementService.database("neo4j");
        registerShutdownHook(managementService);

        Launcher launcher = new Launcher();
        launcher.addInputResource(src);
        launcher.getEnvironment().setNoClasspath(true);
        CtModel model = launcher.buildModel();

        Map<String, Node> nodes = new HashMap<>();

        try (Transaction tx = graphDb.beginTx()) {

            // 1. Packages
            for (CtPackage p : model.getAllPackages()) {
                if (p.isUnnamedPackage()) continue;
                String pkgId = "pkg:" + p.getQualifiedName();
                Node pn = createNode(tx, pkgId, "Package", p.getQualifiedName(), "", "");
                nodes.put(pkgId, pn);
            }

            // 2. Classes / Interfaces
            for (CtType<?> t : model.getAllTypes()) {
                String qname = t.getQualifiedName();
                String classId = "class:" + qname;
                Node cn = createNode(tx, classId, t.isClass() ? "Class" : "Interface",
                        qname,
                        t.getPosition() != null ? t.getPosition().getFile().getPath() : "",
                        t.toString());
                nodes.put(classId, cn);

                // 包含关系
                CtPackage pkg = t.getPackage();
                if (pkg != null && !pkg.isUnnamedPackage()) {
                    String pkgId = "pkg:" + pkg.getQualifiedName();
                    Node pkgNode = nodes.get(pkgId);
                    if (pkgNode != null) {
                        createRelationship(tx, pkgNode, cn, RelTypes.CONTAINS);
                    }
                }

                // 继承/实现
                if (t.getSuperclass() != null) {
                    String superClassName = "class:" + t.getSuperclass().getQualifiedName();
                    Node superClassNode = nodes.get(superClassName);
                    if (superClassNode != null) {
                        createRelationship(tx, cn, superClassNode, RelTypes.EXTENDS);
                    }
                }
                for (CtTypeReference<?> sup : t.getSuperInterfaces()) {
                    String interfaceName = "class:" + sup.getQualifiedName();
                    Node interfaceNode = nodes.get(interfaceName);
                    if (interfaceNode != null) {
                        createRelationship(tx, cn, interfaceNode, RelTypes.IMPLEMENTS);
                    }
                }
            }

            // 3. Methods
            for (CtMethod<?> m : model.getElements(e -> e instanceof CtMethod).stream().map(e -> (CtMethod<?>) e).toList()) {
                String mid = "method:" + m.getDeclaringType().getQualifiedName() + "#" + m.getSimpleName() + ":" + m.getSignature();
                Node mn = createNode(tx, mid, "Method", m.getSimpleName(),
                        m.getPosition() != null ? m.getPosition().getFile().getPath() : "", m.toString());
                nodes.put(mid, mn);

                // class contains method
                String owner = "class:" + m.getDeclaringType().getQualifiedName();
                Node ownerNode = nodes.get(owner);
                if (ownerNode != null) {
                    createRelationship(tx, ownerNode, mn, RelTypes.CONTAINS);
                }

                // method calls
                for (CtInvocation<?> inv : m.getElements(e -> e instanceof CtInvocation).stream().map(e -> (CtInvocation<?>) e).toList()) {
                    try {
                        CtExecutableReference<?> execRef = inv.getExecutable();
                        CtTypeReference<?> declaring = execRef.getDeclaringType();
                        if (declaring != null) {
                            String calleeId = "method:" + declaring.getQualifiedName() + "#" + execRef.getSimpleName() + ":" + execRef.getSignature();
                            Node calleeNode = nodes.get(calleeId);
                            if (calleeNode != null) {
                                createRelationship(tx, mn, calleeNode, RelTypes.CALLS);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

            tx.commit();
            System.out.println("Neo4j graph database created at " + graphDbPath);
        }

        managementService.shutdown();
    }

    private static Node createNode(Transaction tx, String id, String type, String name, String file, String source) {
        Node node = tx.createNode(Label.label(type));
        node.setProperty("uid", id);
        node.setProperty("type", type);
        node.setProperty("name", name);
        node.setProperty("file", file);
        node.setProperty("source", source);
        return node;
    }

    private static void createRelationship(Transaction tx, Node from, Node to, RelationshipType type) {
        if (from != null && to != null) {
            from.createRelationshipTo(to, type);
        }
    }

    private static void registerShutdownHook(final DatabaseManagementService managementService) {
        Runtime.getRuntime().addShutdownHook(new Thread(managementService::shutdown));
    }
}
