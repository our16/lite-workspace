package org.example.liteworkspace;

import org.neo4j.graphdb.*;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.util.*;

public class QueryGraphDb {

    private static DatabaseManagementService managementService;
    private static GraphDatabaseService graphDb;

    public static void main(String[] args) {
        String graphDbPath = "graphdb"; // 数据库目录
        graphDb = initDb(graphDbPath);

        try (Transaction tx = graphDb.beginTx()) {
            // 查询所有类及其方法
            String query = "MATCH (c:Class)-[:CONTAINS]->(m:Method) " +
                    "OPTIONAL MATCH (m)-[:CALLS]->(callee:Method) " +
                    "RETURN c.name AS className, m.name AS methodName, collect(callee.name) AS calls";

            Result result = tx.execute(query);

            List<Map<String, Object>> output = new ArrayList<>();
            while (result.hasNext()) {
                Map<String, Object> row = result.next();
                Map<String, Object> item = new HashMap<>();
                item.put("class", row.get("className"));
                item.put("method", row.get("methodName"));
                item.put("calls", row.get("calls"));
                output.add(item);
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(output);
            System.out.println(json);

            tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            shutdownDb();
        }
    }

    private static GraphDatabaseService initDb(String path) {
        managementService = new DatabaseManagementServiceBuilder(new File(path).toPath()).build();
        GraphDatabaseService db = managementService.database("neo4j");
        registerShutdownHook(managementService);
        return db;
    }

    private static void shutdownDb() {
        if (managementService != null) {
            managementService.shutdown();
        }
    }

    private static void registerShutdownHook(final DatabaseManagementService managementService) {
        Runtime.getRuntime().addShutdownHook(new Thread(managementService::shutdown));
    }
}
