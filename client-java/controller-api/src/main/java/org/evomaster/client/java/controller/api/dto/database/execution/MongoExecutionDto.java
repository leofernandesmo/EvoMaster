package org.evomaster.client.java.controller.api.dto.database.execution;


import java.util.ArrayList;
import java.util.List;

public class MongoExecutionDto {
    public List<MongoFailedQuery> failedQueries = new ArrayList<>();
}